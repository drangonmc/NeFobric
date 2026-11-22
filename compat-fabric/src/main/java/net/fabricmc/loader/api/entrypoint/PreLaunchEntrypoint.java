package net.fabricmc.loader.api.entrypoint;

/** 在游戏类加载之前运行的入口（键 “preLaunch”）。 */
@FunctionalInterface
public interface PreLaunchEntrypoint {
    void onPreLaunch();
}
