package cpw.mods.modlauncher;

import dev.nefobric.compat.forge.transform.LaunchPluginBridge;

/**
 * ModLauncher 的入口对象。NeFobric 不用 ModLauncher 启动，这里只保留模组会碰的部分：{@link #INSTANCE} 和
 * 启动插件表 {@code launchPlugins}（字段名和 ModLauncher 的一致，模组会用反射往里加插件）。
 * 第一次用到时把插件分派挂到类变换流水线上。
 */
public class Launcher {
    public static Launcher INSTANCE;

    private final LaunchPluginHandler launchPlugins;

    static {
        INSTANCE = new Launcher();
    }

    private Launcher() {
        this.launchPlugins = new LaunchPluginHandler();
        LaunchPluginHandler handler = this.launchPlugins;
        LaunchPluginBridge.install(handler::transform);
    }
}
