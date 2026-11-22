package dev.nefobric.compat.fabric.impl;

import dev.nefobric.compat.fabric.entrypoint.EntrypointStorage;
import dev.nefobric.loader.Env;
import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.metadata.ModCandidate;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * {@link FabricLoader} 的实现，数据来自 NeFobric 核心。模组列表和入口表在第一次使用时建立
 * （最早可能在 Mixin 插件里，早于 preLaunch）。
 */
public final class FabricLoaderImpl implements FabricLoader {
    public static final FabricLoaderImpl INSTANCE = new FabricLoaderImpl();

    private static final java.util.Set<String> SENSITIVE_ARGS = java.util.Set.of("accesstoken", "clientid",
            "profileproperties", "proxypass", "proxyuser", "username", "userproperties", "uuid", "xuid");

    private final ObjectShare objectShare = new ObjectShareImpl();
    private final MappingResolver mappingResolver = new MappingResolverImpl();
    private volatile ModRegistry registry;
    private volatile EntrypointStorage entrypoints;

    private FabricLoaderImpl() {
    }

    private static NeFobricLoader loader() {
        return NeFobricLoader.get();
    }

    ModRegistry registry() {
        ModRegistry r = registry;
        if (r == null) {
            synchronized (this) {
                r = registry;
                if (r == null) {
                    registry = r = ModRegistry.build(loader());
                }
            }
        }
        return r;
    }

    public EntrypointStorage entrypointStorage() {
        EntrypointStorage e = entrypoints;
        if (e == null) {
            synchronized (this) {
                e = entrypoints;
                if (e == null) {
                    entrypoints = e = EntrypointStorage.create(registry().all(), loader().classLoader());
                }
            }
        }
        return e;
    }

    /** 核心模组对应的容器。 */
    public ModContainerImpl container(ModCandidate candidate) {
        return registry().get(candidate);
    }

    public List<ModContainerImpl> containers() {
        return registry().all();
    }

    @Override
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        return entrypointStorage().getEntrypoints(key, type);
    }

    @Override
    public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        return entrypointStorage().getEntrypointContainers(key, type);
    }

    @Override
    public <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
        entrypointStorage().invoke(key, type, invoker);
    }

    @Override
    public ObjectShare getObjectShare() {
        return objectShare;
    }

    @Override
    public MappingResolver getMappingResolver() {
        return mappingResolver;
    }

    @Override
    public Optional<ModContainer> getModContainer(String id) {
        return registry().get(id).map(c -> c);
    }

    @Override
    public Collection<ModContainer> getAllMods() {
        return registry().asContainers();
    }

    @Override
    public boolean isModLoaded(String id) {
        return registry().get(id).isPresent() || loader().isModLoaded(id);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return loader().isDevelopmentEnvironment() || Boolean.getBoolean("fabric.development");
    }

    @Override
    public EnvType getEnvironmentType() {
        return loader().env() == Env.CLIENT ? EnvType.CLIENT : EnvType.SERVER;
    }

    @Override
    @Deprecated
    public Object getGameInstance() {
        return CoreBridge.gameInstance();
    }

    @Override
    public Path getGameDir() {
        return loader().gameDir();
    }

    @Override
    @Deprecated
    public File getGameDirectory() {
        return getGameDir().toFile();
    }

    /** 与 Fabric Loader 相同：目录不存在时创建。 */
    @Override
    public Path getConfigDir() {
        Path dir = loader().configDir();
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new UncheckedIOException("Creating config directory " + dir + " failed", e);
            }
        }
        return dir;
    }

    @Override
    @Deprecated
    public File getConfigDirectory() {
        return getConfigDir().toFile();
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        String[] args = loader().launchArguments();
        if (!sanitize) {
            return args;
        }
        // 与 Fabric Loader 相同的敏感参数表：去掉参数名和它的值
        List<String> out = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--") && SENSITIVE_ARGS.contains(a.substring(2).toLowerCase(java.util.Locale.ROOT))) {
                i++;
                continue;
            }
            out.add(a);
        }
        return out.toArray(new String[0]);
    }
}
