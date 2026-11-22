package dev.nefobric.loader.launch;

import dev.nefobric.loader.Env;
import dev.nefobric.loader.NeFobric;
import dev.nefobric.loader.classloading.NeFobricClassLoader;
import dev.nefobric.loader.discovery.ModDiscoverer;
import dev.nefobric.loader.hook.GameEntryPatch;
import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.mapping.MappingStore;
import dev.nefobric.loader.mapping.RemapCache;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.mixin.MixinSetup;
import dev.nefobric.loader.resolve.ModResolver;
import dev.nefobric.loader.spi.EcosystemAdapter;
import dev.nefobric.loader.spi.LoadStage;
import dev.nefobric.loader.spi.ModLoadingException;
import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 启动流程：找游戏 → 发现模组 → 解析依赖 → 建类加载器并装上游戏钩子和各兼容层的变换器 → 启动 Mixin → preLaunch
 * → 移交原版主类。
 * 模组的其余初始化在游戏钩子触发时进行（见 {@link NeFobricLoader#onGameStart()}）。
 */
public final class Launcher {

    private Launcher() {
    }

    /** {@link ClientMain} 和 {@link ServerMain} 的共同入口。 */
    static void main(Env env, String[] args) {
        MethodHandle gameMain;
        try {
            gameMain = prepare(env, args);
        } catch (NeFobricStartupException e) {
            CrashReporter.startupFailure(e.problems());
            System.exit(1);
            return;
        } catch (ModLoadingException e) {
            CrashReporter.modFailure(e, NeFobricLoader.isInitialized() ? NeFobricLoader.get() : null);
            System.exit(1);
            return;
        } catch (Throwable t) {
            CrashReporter.unexpected(t);
            System.exit(1);
            return;
        }
        try {
            gameMain.invokeExact(args);
        } catch (Throwable t) {
            throw Launcher.<RuntimeException>sneakyThrow(t);
        }
    }

    static MethodHandle prepare(Env env, String[] rawArgs) throws Exception {
        LaunchArgs args = LaunchArgs.parse(env, rawArgs);
        Log.openFile(args.gameDir().resolve("logs").resolve("nefobric-latest.log"));
        CrashReporter.setContext(args.gameDir(), List.of());
        Log.info(Messages.tr("launch.start", NeFobric.VERSION, NeFobric.TARGET_MINECRAFT, env.displayName()));
        Log.info(Messages.tr("launch.java", System.getProperty("java.version"), System.getProperty("java.vendor"),
                System.getProperty("os.name") + " " + System.getProperty("os.arch")));
        Log.info(Messages.tr("launch.gameDir", args.gameDir()));

        // 1. 找游戏
        GameInstall game = GameLocator.locate(args);
        Log.info(Messages.tr("launch.gameJar", game.gameJar(),
                game.version() == null ? Messages.tr("game.version.unknown") : game.version()));
        if (game.version() != null && !NeFobric.TARGET_MINECRAFT.equals(game.version())) {
            Log.warn(Messages.tr("launch.versionMismatch", NeFobric.TARGET_MINECRAFT, game.version()));
        }
        // 原版是混淆的：先读映射表，把游戏重映射成运行时命名空间（Mojang 类名 + SRG 成员名，和 Forge 一致）
        MappingStore.Loaded maps;
        try {
            maps = MappingStore.load(args.gameDir(), game);
        } catch (MappingStore.MissingMappingsException e) {
            throw new NeFobricStartupException(List.of(e.getMessage()));
        }
        if (!maps.isIdentity()) {
            game = game.withGameJar(RemapCache.game(args.gameDir(), game, maps));
        }

        // 2. 发现模组
        List<EcosystemAdapter> adapters = loadAdapters();
        Path modsDir = args.modsDir();
        if (!Files.isDirectory(modsDir)) {
            Files.createDirectories(modsDir);
            Log.info(Messages.tr("launch.modsDirCreated", modsDir));
        }
        List<Path> jars = ModDiscoverer.findJars(modsDir);
        ModDiscoverer.Result discovered = ModDiscoverer.read(jars, adapters);
        discovered.notes().forEach(Log::info);
        discovered.warnings().forEach(Log::warn);

        // 3. 解析依赖
        List<ModCandidate> builtins = new ArrayList<>(CoreBuiltins.create(game));
        for (EcosystemAdapter a : adapters) {
            builtins.addAll(a.builtinMods(game));
        }
        ModResolver.Result resolved = new ModResolver(env).resolve(discovered.candidates(), builtins);
        resolved.notes().forEach(Log::info);
        resolved.warnings().forEach(Log::warn);
        CrashReporter.setContext(args.gameDir(), resolved.mods());
        List<String> problems = new ArrayList<>(discovered.errors());
        problems.addAll(resolved.errors());
        if (!problems.isEmpty()) {
            throw new NeFobricStartupException(problems);
        }
        printModList(resolved.mods(), builtins);
        for (ModCandidate m : resolved.mods()) {
            for (EcosystemAdapter a : adapters) {
                if (a.owns(m)) {
                    a.warningsFor(m).forEach(Log::warn);
                }
            }
        }

        // 4. 需要重映射的模组（Fabric 模组是 intermediary 名）换成缓存里重映射后的 jar
        Map<ModCandidate, Path> runtimePaths = remapMods(args.gameDir(), resolved.mods(), maps);

        // 5. 类加载器与游戏钩子
        Set<String> loaderOwned = new LinkedHashSet<>();
        for (EcosystemAdapter a : adapters) {
            loaderOwned.addAll(a.loaderOwnedClasses());
        }
        List<Path> ownJars = new ArrayList<>();
        ownJars.add(game.gameJar());
        ownJars.addAll(runtimeLibraries(adapters, game, args.gameDir().resolve(".nefobric").resolve("cache")));
        resolved.mods().stream().map(m -> runtimePaths.getOrDefault(m, m.origin())).distinct().forEach(ownJars::add);
        NeFobricClassLoader classLoader = new NeFobricClassLoader(ownJars, parentLoader(game), loaderOwned);
        GameEntryPatch entryPatch = new GameEntryPatch(env);
        classLoader.addTransformer(entryPatch);
        NeFobricLoader loader = NeFobricLoader.initialize(args, game, adapters, resolved.mods(), resolved.byId(),
                classLoader, maps.mappings(), runtimePaths);
        Thread.currentThread().setContextClassLoader(classLoader);
        for (EcosystemAdapter a : adapters) {
            a.registerTransformers(classLoader.transformers(), loader, resolved.mods().stream().filter(a::owns).toList());
        }

        // 6. Mixin：注册各模组的配置，就绪后挂到类变换流水线的最后
        startMixin(env, classLoader, adapters, resolved.mods());

        // 7. preLaunch，然后加载（并修改）原版主类
        loader.runStage(LoadStage.PRE_LAUNCH);
        String mainClassName = entryPatch.targetClass();
        Class<?> mainClass = Class.forName(mainClassName, false, classLoader);
        if (!entryPatch.isApplied()) {
            loader.onGameStart();
        }
        Log.info(Messages.tr("launch.handoff", mainClassName));
        return MethodHandles.publicLookup().findStatic(mainClass, "main",
                MethodType.methodType(void.class, String[].class));
    }

    private static List<Path> runtimeLibraries(List<EcosystemAdapter> adapters, GameInstall game, Path cacheDir)
            throws NeFobricStartupException {
        List<Path> out = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (EcosystemAdapter a : adapters) {
            try {
                out.addAll(a.runtimeLibraries(game, cacheDir));
            } catch (IOException e) {
                problems.add(Messages.tr("launch.runtimeLibraryFailed", a.displayName(), e));
            }
        }
        if (!problems.isEmpty()) {
            throw new NeFobricStartupException(problems);
        }
        return out;
    }

    private static void startMixin(Env env, NeFobricClassLoader classLoader, List<EcosystemAdapter> adapters,
                                   List<ModCandidate> mods) throws NeFobricStartupException {
        List<MixinSetup.ConfigRequest> requests = new ArrayList<>();
        for (ModCandidate m : mods) {
            for (EcosystemAdapter a : adapters) {
                if (a.owns(m)) {
                    for (String config : a.mixinConfigs(m, env)) {
                        requests.add(new MixinSetup.ConfigRequest(config, m, a));
                    }
                    break;
                }
            }
        }
        List<String> problems = MixinSetup.start(classLoader, env, requests);
        if (!problems.isEmpty()) {
            throw new NeFobricStartupException(problems);
        }
    }

    private static Map<ModCandidate, Path> remapMods(Path gameDir, List<ModCandidate> mods, MappingStore.Loaded maps)
            throws NeFobricStartupException {
        Map<ModCandidate, Path> result = new LinkedHashMap<>();
        if (maps.isIdentity()) {
            return result;
        }
        String runtime = maps.mappings().runtimeNamespace();
        Map<Path, Path> done = new HashMap<>();
        List<String> problems = new ArrayList<>();
        for (ModCandidate m : mods) {
            String ns = m.sourceNamespace();
            if (ns == null || ns.equals(runtime) || m.origin() == null) {
                continue;
            }
            if (!maps.data().hasNamespace(ns)) {
                problems.add(Messages.tr("remap.failed", m.describe(), "unknown namespace " + ns));
                continue;
            }
            Path out = done.get(m.origin());
            if (out == null) {
                try {
                    out = RemapCache.mod(gameDir, m.origin(), ns, maps);
                } catch (Exception e) {
                    problems.add(Messages.tr("remap.failed", m.describe(), e));
                    continue;
                }
                done.put(m.origin(), out);
            }
            result.put(m, out);
        }
        if (!problems.isEmpty()) {
            throw new NeFobricStartupException(problems);
        }
        return result;
    }

    private static List<EcosystemAdapter> loadAdapters() {
        List<EcosystemAdapter> list = new ArrayList<>();
        for (EcosystemAdapter a : ServiceLoader.load(EcosystemAdapter.class, Launcher.class.getClassLoader())) {
            list.add(a);
        }
        list.sort(Comparator.comparingInt(EcosystemAdapter::priority));
        Log.debug("Compatibility layers: " + list.stream().map(EcosystemAdapter::displayName).toList());
        return list;
    }

    /** 客户端：启动器已把所有库放在 classpath 上。专用服务端：库来自 bundler，单独建一层。 */
    private static ClassLoader parentLoader(GameInstall game) throws MalformedURLException {
        ClassLoader self = Launcher.class.getClassLoader();
        if (game.libraries().isEmpty()) {
            return self;
        }
        URL[] urls = new URL[game.libraries().size()];
        for (int i = 0; i < urls.length; i++) {
            urls[i] = game.libraries().get(i).toUri().toURL();
        }
        return new URLClassLoader("NeFobric-Libraries", urls, self);
    }

    private static void printModList(List<ModCandidate> mods, List<ModCandidate> builtins) {
        if (mods.isEmpty()) {
            Log.info(Messages.tr("launch.noMods"));
        } else {
            Map<Ecosystem, Long> counts = new EnumMap<>(Ecosystem.class);
            for (ModCandidate m : mods) {
                counts.merge(m.ecosystem(), 1L, Long::sum);
            }
            String summary = counts.entrySet().stream()
                    .map(e -> e.getKey().displayName() + " " + e.getValue())
                    .collect(Collectors.joining(", "));
            StringBuilder sb = new StringBuilder(Messages.tr("launch.modList", mods.size(), summary));
            int idWidth = mods.stream().mapToInt(m -> m.id().length()).max().orElse(0);
            int verWidth = mods.stream().mapToInt(m -> m.version().length()).max().orElse(0);
            for (ModCandidate m : mods) {
                sb.append('\n').append(String.format("  - %-" + idWidth + "s  %-" + verWidth + "s  %-8s  %s",
                        m.id(), m.version(), m.ecosystem().displayName(), m.fileName()));
            }
            Log.info(sb.toString());
        }
        Log.info(Messages.tr("launch.builtins", builtins.stream().map(b -> b.id() + " " + b.version())
                .collect(Collectors.joining(", "))));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}
