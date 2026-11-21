package dev.nefobric.loader.hook;

import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.util.Log;

/**
 * 被插入到原版代码里的静态方法。这个类由父加载器（加载器自己）提供，被修改过的游戏类调用它时看到的是同一份状态。
 */
public final class GameHooks {
    private GameHooks() {
    }

    /** 客户端：渲染线程上、Bootstrap 之后、游戏对象构造之前。 */
    public static void onClientStart() {
        start();
    }

    /** 专用服务端：Bootstrap 之后、读取 server.properties 之前。 */
    public static void onServerStart() {
        start();
    }

    private static void start() {
        if (!NeFobricLoader.isInitialized()) {
            Log.warn("Game hook fired but NeFobric is not initialized; ignoring.");
            return;
        }
        NeFobricLoader.get().onGameStart();
    }
}
