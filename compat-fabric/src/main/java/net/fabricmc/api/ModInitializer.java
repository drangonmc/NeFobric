package net.fabricmc.api;

/**
 * Fabric 模组的通用入口（fabric.mod.json 里的 {@code "main"}）。签名与 Fabric Loader 一致。
 */
@FunctionalInterface
public interface ModInitializer {
    void onInitialize();
}
