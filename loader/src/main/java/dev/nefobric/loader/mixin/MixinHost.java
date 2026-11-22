package dev.nefobric.loader.mixin;

import dev.nefobric.loader.Env;
import dev.nefobric.loader.classloading.NeFobricClassLoader;

/**
 * Mixin 用无参构造创建服务对象，拿不到参数；启动时把游戏类加载器和当前端记在这里。
 * 一个进程只有一套 Mixin 环境，所以这里也只有一份。
 */
final class MixinHost {
    private static volatile NeFobricClassLoader classLoader;
    private static volatile Env env;

    private MixinHost() {
    }

    static void set(NeFobricClassLoader loader, Env side) {
        classLoader = loader;
        env = side;
    }

    static NeFobricClassLoader classLoader() {
        NeFobricClassLoader cl = classLoader;
        if (cl == null) {
            throw new IllegalStateException("Mixin was started before NeFobric set up the game class loader");
        }
        return cl;
    }

    static Env env() {
        return env;
    }
}
