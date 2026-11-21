package net.fabricmc.loader.api;

import dev.nefobric.compat.fabric.FabricLoaderShim;
import net.fabricmc.api.EnvType;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Fabric Loader 的公开接口，由 NeFobric 实现。Alpha 只提供简单模组最常用的方法，签名与 Fabric Loader 0.14+ 一致；
 * {@code getModContainer}、{@code getAllMods}、{@code getMappingResolver} 等会在后续版本补上。
 */
public interface FabricLoader {

    static FabricLoader getInstance() {
        return FabricLoaderShim.INSTANCE;
    }

    /** 返回所有 Fabric 模组在 {@code key} 下声明、且实现了 {@code type} 的入口实例。 */
    <T> List<T> getEntrypoints(String key, Class<T> type);

    boolean isModLoaded(String id);

    boolean isDevelopmentEnvironment();

    EnvType getEnvironmentType();

    Path getGameDir();

    @Deprecated
    File getGameDirectory();

    Path getConfigDir();

    @Deprecated
    File getConfigDirectory();

    String[] getLaunchArguments(boolean sanitize);
}
