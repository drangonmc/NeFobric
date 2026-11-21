package net.fabricmc.api;

/** Fabric 模组的客户端入口（{@code "client"}），只在客户端运行。 */
@FunctionalInterface
public interface ClientModInitializer {
    void onInitializeClient();
}
