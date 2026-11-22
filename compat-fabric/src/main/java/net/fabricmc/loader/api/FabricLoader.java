package net.fabricmc.loader.api;

import dev.nefobric.compat.fabric.impl.FabricLoaderImpl;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Fabric Loader 的公开接口，由 NeFobric 实现，方法签名与 Fabric Loader 0.16 一致。
 * Fabric 模组通过它也能看到 Forge 系模组和内置模组（minecraft、java、forge 等）。
 */
public interface FabricLoader {
    static FabricLoader getInstance() {
        return FabricLoaderImpl.INSTANCE;
    }

    <T> List<T> getEntrypoints(String key, Class<T> type);

    <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type);

    <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker);

    ObjectShare getObjectShare();

    MappingResolver getMappingResolver();

    Optional<ModContainer> getModContainer(String id);

    Collection<ModContainer> getAllMods();

    boolean isModLoaded(String id);

    boolean isDevelopmentEnvironment();

    EnvType getEnvironmentType();

    /** 客户端是 {@code Minecraft} 实例，专用服务端是 {@code DedicatedServer} 实例；还没创建时为 {@code null}。 */
    @Deprecated
    Object getGameInstance();

    Path getGameDir();

    @Deprecated
    File getGameDirectory();

    Path getConfigDir();

    @Deprecated
    File getConfigDirectory();

    String[] getLaunchArguments(boolean sanitize);
}
