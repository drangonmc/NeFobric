package net.fabricmc.api;

/** Fabric 模组的专用服务端入口（{@code "server"}），只在专用服务端运行。 */
@FunctionalInterface
public interface DedicatedServerModInitializer {
    void onInitializeServer();
}
