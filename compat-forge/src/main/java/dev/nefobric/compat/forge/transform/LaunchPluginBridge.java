package dev.nefobric.compat.forge.transform;

import dev.nefobric.loader.classloading.ClassTransformer;

/**
 * ModLauncher 启动插件（{@code cpw.mods.modlauncher.serviceapi.ILaunchPluginService}）在类变换流水线里的位置。
 * 插件本身和分派逻辑在 Forge 运行时里（游戏类加载器），这里只是个挂钩：运行时第一次用到
 * {@code cpw.mods.modlauncher.Launcher} 时把分派器装上，之前什么都不做。
 */
public final class LaunchPluginBridge implements ClassTransformer {
    /** 运行时那边的分派器：没有插件要处理这个类时原样返回 {@code bytes}。 */
    public interface Dispatcher {
        byte[] transform(String className, byte[] bytes);
    }

    private static volatile Dispatcher dispatcher;

    public static void install(Dispatcher d) {
        dispatcher = d;
    }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        Dispatcher d = dispatcher;
        return d == null ? bytes : d.transform(className, bytes);
    }
}
