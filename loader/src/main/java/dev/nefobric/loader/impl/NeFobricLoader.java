package dev.nefobric.loader.impl;

import dev.nefobric.loader.Env;
import dev.nefobric.loader.classloading.NeFobricClassLoader;
import dev.nefobric.loader.launch.CrashReporter;
import dev.nefobric.loader.launch.GameInstall;
import dev.nefobric.loader.launch.LaunchArgs;
import dev.nefobric.loader.mapping.Mappings;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.spi.EcosystemAdapter;
import dev.nefobric.loader.spi.LoadStage;
import dev.nefobric.loader.spi.ModLoadingException;
import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;

import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 加载器运行时状态：当前端、游戏目录、已解析的模组和类加载器。兼容层（例如 Fabric 的 {@code FabricLoader}）
 * 从这里取数据。
 */
public final class NeFobricLoader {
    private static volatile NeFobricLoader instance;

    private final LaunchArgs args;
    private final GameInstall game;
    private final List<EcosystemAdapter> adapters;
    private final List<ModCandidate> mods;
    private final Map<String, ModCandidate> modsById;
    private final NeFobricClassLoader classLoader;
    private final Mappings mappings;
    private final Map<ModCandidate, Path> runtimePaths;
    private volatile Object gameInstance;
    private final Set<LoadStage> completed = EnumSet.noneOf(LoadStage.class);
    private boolean gameStarted;

    private NeFobricLoader(LaunchArgs args, GameInstall game, List<EcosystemAdapter> adapters,
                           List<ModCandidate> mods, Map<String, ModCandidate> modsById,
                           NeFobricClassLoader classLoader, Mappings mappings, Map<ModCandidate, Path> runtimePaths) {
        this.args = args;
        this.game = game;
        this.adapters = List.copyOf(adapters);
        this.mods = List.copyOf(mods);
        this.modsById = Map.copyOf(modsById);
        this.classLoader = classLoader;
        this.mappings = mappings;
        this.runtimePaths = Map.copyOf(runtimePaths);
    }

    public static synchronized NeFobricLoader initialize(LaunchArgs args, GameInstall game,
                                                         List<EcosystemAdapter> adapters, List<ModCandidate> mods,
                                                         Map<String, ModCandidate> modsById,
                                                         NeFobricClassLoader classLoader, Mappings mappings,
                                                         Map<ModCandidate, Path> runtimePaths) {
        if (instance != null) {
            throw new IllegalStateException("NeFobric is already initialized");
        }
        instance = new NeFobricLoader(args, game, adapters, mods, modsById, classLoader, mappings, runtimePaths);
        return instance;
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    public static NeFobricLoader get() {
        NeFobricLoader i = instance;
        if (i == null) {
            throw new IllegalStateException("NeFobric is not initialized");
        }
        return i;
    }

    public Env env() {
        return args.env();
    }

    public Path gameDir() {
        return args.gameDir();
    }

    public Path configDir() {
        return args.configDir();
    }

    public String[] launchArguments() {
        return args.raw().clone();
    }

    public GameInstall game() {
        return game;
    }

    /** 已加载的模组（不含内置模组），按加载顺序。 */
    public List<ModCandidate> mods() {
        return mods;
    }

    public Collection<ModCandidate> allModsIncludingBuiltin() {
        return modsById.values().stream().distinct().toList();
    }

    public Optional<ModCandidate> mod(String id) {
        return Optional.ofNullable(modsById.get(id));
    }

    public boolean isModLoaded(String id) {
        return modsById.containsKey(id);
    }

    public NeFobricClassLoader classLoader() {
        return classLoader;
    }

    /** 名字映射；运行时命名空间见 {@link Mappings#runtimeNamespace()}。 */
    public Mappings mappings() {
        return mappings;
    }

    /**
     * 模组实际加载的 jar：需要重映射的模组（例如 Fabric 模组）是缓存里重映射后的文件，其余就是 {@link ModCandidate#origin()}。
     */
    public Path runtimePath(ModCandidate mod) {
        return runtimePaths.getOrDefault(mod, mod.origin());
    }

    /**
     * 游戏对象：客户端是 {@code net.minecraft.client.Minecraft}，专用服务端是 {@code MinecraftServer}。
     * 游戏钩子触发之前为 {@code null}。
     */
    public Object gameInstance() {
        return gameInstance;
    }

    /** 由游戏钩子调用。 */
    public void setGameInstance(Object instance) {
        this.gameInstance = instance;
    }

    public boolean isDevelopmentEnvironment() {
        return Boolean.getBoolean("nefobric.development");
    }

    /** 每个阶段只运行一次，按兼容层优先级依次交给它们。 */
    public synchronized void runStage(LoadStage stage) throws ModLoadingException {
        if (!completed.add(stage)) {
            return;
        }
        Log.debug("Stage " + stage);
        for (EcosystemAdapter adapter : adapters) {
            List<ModCandidate> owned = mods.stream().filter(adapter::owns).toList();
            if (!owned.isEmpty()) {
                adapter.runStage(stage, this, owned);
            }
        }
    }

    /**
     * 游戏钩子触发：依次构造 Forge 系模组、运行 Fabric main、再运行当前端的入口。出错时写崩溃报告并抛出异常，
     * 让游戏按自己的方式崩溃（客户端会生成原版崩溃报告）。
     */
    public synchronized void onGameStart() {
        if (gameStarted) {
            return;
        }
        gameStarted = true;
        long start = System.nanoTime();
        try {
            runStage(LoadStage.CONSTRUCT);
            runStage(LoadStage.MAIN);
            runStage(LoadStage.SIDE);
        } catch (ModLoadingException e) {
            CrashReporter.modFailure(e, this);
            throw new ModInitializationError(e.getMessage(), e.getCause());
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        Log.info(Messages.tr("load.done", mods.size(), ms));
    }
}
