package dev.nefobric.compat.forge;

import dev.nefobric.compat.forge.transform.AccessTransformerApplier;
import dev.nefobric.compat.forge.transform.AccessTransformers;
import dev.nefobric.compat.forge.transform.EnumExtender;
import dev.nefobric.compat.forge.transform.ExtensibleVanillaEnums;
import dev.nefobric.compat.forge.transform.OnlyInStripper;
import dev.nefobric.loader.Env;
import dev.nefobric.loader.discovery.ModJar;
import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.launch.GameInstall;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.ModMetadataException;
import dev.nefobric.loader.spi.EcosystemAdapter;
import dev.nefobric.loader.spi.LoadStage;
import dev.nefobric.loader.spi.ModLoadingException;
import dev.nefobric.loader.transform.TransformerPhase;
import dev.nefobric.loader.transform.TransformerRegistry;
import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Forge 系兼容层（1.20.1 上 Forge 47 与 NeoForge 47.1 共用）：读 mods.toml，扫描 {@code @Mod} 主类。
 *
 * <p>Forge 的 API（事件总线、FML 加载流程、配置……）由 NeFobric 自己重写，放在内嵌的 forge-runtime.jar 里
 * （见 compat-forge/build.gradle）。它引用原版类，所以由游戏类加载器加载：启动时解压到缓存目录交给核心，
 * 游戏钩子触发时通过反射调用 {@code dev.nefobric.forge.fml.ForgeRuntime} 跑 Forge 的加载状态机。</p>
 */
public final class ForgeAdapter implements EcosystemAdapter {
    /** 对外声明的版本号，只为通过模组的依赖检查，不代表已经实现了对应版本的 API。 */
    public static final String EMULATED_FORGE_VERSION = "47.4.0";
    public static final String EMULATED_NEOFORGE_VERSION = "47.1.106";
    /** mods.toml 里 loaderVersion 对照的 FML 语言加载器版本。 */
    public static final String FML_LANGUAGE_VERSION = "47";

    /** 内嵌运行库的清单，每行一个相对 META-INF/nefobric/ 的路径。 */
    static final String RUNTIME_LIST = "META-INF/nefobric/forge-runtime.list";
    static final String RUNTIME_ENTRY_CLASS = "dev.nefobric.forge.fml.ForgeRuntime";

    /** 读到过 Forge 系模组才加载 Forge 运行时：否则 Fabric 模组按类名探测 Forge 时会误以为在 Forge 上。 */
    private volatile boolean sawForgeMods;

    @Override
    public String displayName() {
        return "Forge/NeoForge";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean hasMetadata(ModJar jar) {
        return jar.has(ModsTomlParser.PATH);
    }

    @Override
    public List<ModCandidate> readMods(ModJar jar) throws ModMetadataException {
        try {
            String toml = jar.readString(ModsTomlParser.PATH).orElseThrow();
            Manifest mf = jar.manifest();
            String implVersion = null;
            List<String> manifestMixins = new ArrayList<>();
            if (mf != null) {
                Attributes main = mf.getMainAttributes();
                implVersion = main.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                String mixinConfigs = main.getValue("MixinConfigs");
                if (mixinConfigs != null) {
                    Arrays.stream(mixinConfigs.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                            .forEach(manifestMixins::add);
                }
            }
            ModAnnotationScanner.Result scan = ModAnnotationScanner.scan(jar);
            Map<String, String> modClasses = scan.modClasses();
            ModsTomlParser.JarInfo info = new ModsTomlParser.JarInfo(jar.path(), implVersion, modClasses,
                    manifestMixins, jar.has("META-INF/accesstransformer.cfg"),
                    jar.has("META-INF/jarjar/metadata.json"));
            List<ModCandidate> mods = ModsTomlParser.parse(toml, info);
            sawForgeMods |= !mods.isEmpty();
            if (modClasses.isEmpty() && !scan.neoModClasses().isEmpty()) {
                // 只有 net.neoforged 的 @Mod：NeoForge 20.2 以后的模组，在 1.20.1 上一构造就会缺类
                throw ModMetadataException.skip(Messages.tr("forge.modernNeoForgeAnnotation",
                        scan.neoModClasses().values().iterator().next()));
            }
            for (Map.Entry<String, String> e : modClasses.entrySet()) {
                if (mods.stream().noneMatch(m -> m.id().equals(e.getKey()))) {
                    Log.warn(Messages.tr("forge.extraModClass", e.getValue(), e.getKey(), jar.fileName()));
                }
            }
            return mods;
        } catch (IOException e) {
            throw new ModMetadataException(e.toString(), e);
        }
    }

    @Override
    public List<ModCandidate> builtinMods(GameInstall game) {
        return List.of(
                ModCandidate.builder("forge", EMULATED_FORGE_VERSION, Ecosystem.BUILTIN)
                        .name("Forge (NeFobric)").build(),
                ModCandidate.builder("neoforge", EMULATED_NEOFORGE_VERSION, Ecosystem.BUILTIN)
                        .name("NeoForge (NeFobric)").build(),
                ModCandidate.builder("javafml", FML_LANGUAGE_VERSION, Ecosystem.BUILTIN)
                        .name("FML Java language provider (NeFobric)").build(),
                ModCandidate.builder("lowcodefml", FML_LANGUAGE_VERSION, Ecosystem.BUILTIN)
                        .name("FML low-code language provider (NeFobric)").build());
    }

    @Override
    public Collection<String> loaderOwnedClasses() {
        // Forge API 由游戏类加载器从 forge-runtime.jar 加载，不归父加载器
        return List.of();
    }

    @Override
    public List<Path> runtimeLibraries(GameInstall game, Path cacheDir) throws IOException {
        if (!sawForgeMods) {
            return List.of();
        }
        return RuntimeExtractor.extract(ForgeAdapter.class.getClassLoader(), RUNTIME_LIST,
                cacheDir.resolve("forge-runtime"));
    }

    @Override
    public boolean owns(ModCandidate mod) {
        return mod.ecosystem().isForgeFamily();
    }

    @Override
    public List<String> warningsFor(ModCandidate mod) {
        List<String> out = new ArrayList<>();
        if ("javafml".equals(mod.extra(ModsTomlParser.EXTRA_MOD_LOADER, String.class))
                && mod.extra(ModsTomlParser.EXTRA_MOD_CLASS, String.class) == null) {
            out.add(Messages.tr("forge.noModClass", mod.describe(), mod.id()));
        }
        return out;
    }

    /**
     * STRIP 阶段按端去掉 {@code @OnlyIn}，ACCESS 阶段应用所有 Forge 系模组（含 Jar-in-Jar 里的）的访问变换。
     * 和 Forge 一样对游戏和模组的类都生效。
     */
    @Override
    public void registerTransformers(TransformerRegistry registry, NeFobricLoader loader, List<ModCandidate> mods) {
        if (mods.isEmpty()) {
            return;
        }
        registry.add(TransformerPhase.GAME_PATCH, new ExtensibleVanillaEnums());
        registry.add(TransformerPhase.STRIP, new OnlyInStripper(loader.env() == Env.CLIENT ? "CLIENT" : "DEDICATED_SERVER"));
        accessTransformers(mods).ifPresent(at -> registry.add(TransformerPhase.ACCESS, new AccessTransformerApplier(at)));
        // 和 Forge 一样，枚举扩展看到的是 AT 改过的类
        registry.add(TransformerPhase.ACCESS, new EnumExtender());
    }

    /** 读出并合并这些模组的 {@code META-INF/accesstransformer.cfg}；一条都没有时返回空。 */
    public static Optional<AccessTransformers> accessTransformers(List<ModCandidate> mods) {
        AccessTransformers.Builder builder = AccessTransformers.builder();
        Set<Path> seen = new HashSet<>();
        int files = 0;
        for (ModCandidate mod : mods) {
            if (mod.accessTransformers().isEmpty() || mod.origin() == null || !seen.add(mod.origin())) {
                continue;
            }
            try (ModJar jar = ModJar.open(mod.origin())) {
                for (String path : mod.accessTransformers()) {
                    Optional<String> text = jar.readString(path);
                    if (text.isPresent()) {
                        builder.parse(text.get(), mod.id());
                        files++;
                    }
                }
            } catch (IOException e) {
                Log.warn(Messages.tr("forge.atUnreadable", mod.describe(), e.toString()));
            }
        }
        builder.problems().forEach(p -> Log.warn(Messages.tr("forge.atBadLine", p)));
        AccessTransformers at = builder.build();
        if (at.isEmpty()) {
            return Optional.empty();
        }
        Log.debug("Forge access transformers: " + at.entryCount() + " entries from " + files + " file(s)");
        return Optional.of(at);
    }

    /**
     * Forge 的 Jar-in-Jar：{@code META-INF/jarjar/metadata.json} 列出的 jar 都直接放在 {@code META-INF/jarjar/} 下。
     * 同一个库的多个版本由核心按模组版本挑选；不是模组的库按出现的 jar 全部加载。
     */
    @Override
    public List<String> nestedJars(ModCandidate mod, ModJar jar) {
        if (!mod.nestedJars().contains("META-INF/jarjar/metadata.json")) {
            return List.of();
        }
        return jar.entries("META-INF/jarjar/", ".jar");
    }

    @Override
    public void runStage(LoadStage stage, NeFobricLoader loader, List<ModCandidate> mods) throws ModLoadingException {
        switch (stage) {
            // 和 Forge 一样：游戏钩子里构造模组、跑注册表（GATHER）
            case CONSTRUCT -> callRuntime(loader, mods, "gather");
            // 没有原版钩子接管时（例如测试用的假游戏）把 setup 和加载完成也跑完
            case SIDE -> callRuntime(loader, mods, "runRemainingIfNoGameHooks");
            default -> {
            }
        }
    }

    private static void callRuntime(NeFobricLoader loader, List<ModCandidate> mods, String method)
            throws ModLoadingException {
        Method m;
        try {
            Class<?> runtime = Class.forName(RUNTIME_ENTRY_CLASS, true, loader.classLoader());
            m = runtime.getMethod(method);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new ModLoadingException(mods.get(0), Messages.tr("forge.runtimeMissing"), e);
        }
        try {
            m.invoke(null);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof ModLoadingException mle) {
                throw mle;
            }
            throw new ModLoadingException(mods.get(0), "Forge " + method, cause);
        } catch (IllegalAccessException e) {
            throw new ModLoadingException(mods.get(0), "Forge " + method, e);
        }
    }
}
