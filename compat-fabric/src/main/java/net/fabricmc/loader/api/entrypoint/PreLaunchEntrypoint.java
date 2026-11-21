package net.fabricmc.loader.api.entrypoint;

/**
 * 最早的入口（{@code "preLaunch"}）：类加载器就绪后、游戏代码运行前。此时不要碰任何游戏类。
 */
@FunctionalInterface
public interface PreLaunchEntrypoint {
    void onPreLaunch();
}
