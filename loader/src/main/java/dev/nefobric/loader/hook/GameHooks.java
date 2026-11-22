package dev.nefobric.loader.hook;

import dev.nefobric.loader.impl.ModInitializationError;
import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;

/**
 * 被插入到原版代码里的静态方法。这个类由父加载器（加载器自己）提供，被修改过的游戏类调用它时看到的是同一份状态。
 */
public final class GameHooks {
    private GameHooks() {
    }

    /**
     * 客户端：游戏对象（{@code Minecraft}）的构造函数里，游戏目录赋值之后。渲染线程已经初始化，
     * {@code Minecraft.getInstance()} 已经可用。
     */
    public static void onClientGameCreated(Object game) {
        if (NeFobricLoader.isInitialized()) {
            NeFobricLoader.get().setGameInstance(game);
        }
        start();
    }

    /** 客户端的退路：找不到游戏对象构造函数里的插入点时，插在主类 “Render thread” 改名之后。 */
    public static void onClientStart() {
        start();
    }

    /**
     * 专用服务端：Bootstrap 之后、读取 server.properties 之前。原版只把异常记进日志然后正常退出（退出码 0），
     * 这里在模组初始化失败时以退出码 1 结束进程，让启动脚本和面板能看出启动失败；崩溃报告此时已经写好。
     */
    public static void onServerStart() {
        try {
            start();
        } catch (ModInitializationError e) {
            Log.error(Messages.tr("load.serverStopping"));
            System.exit(1);
        }
    }

    private static void start() {
        if (!NeFobricLoader.isInitialized()) {
            Log.warn("Game hook fired but NeFobric is not initialized; ignoring.");
            return;
        }
        NeFobricLoader.get().onGameStart();
    }
}
