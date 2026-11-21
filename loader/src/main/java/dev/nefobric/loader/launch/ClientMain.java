package dev.nefobric.loader.launch;

import dev.nefobric.loader.Env;

/** 客户端入口：启动器版本文件里的 {@code mainClass}。参数与原版客户端相同。 */
public final class ClientMain {
    private ClientMain() {
    }

    public static void main(String[] args) {
        Launcher.main(Env.CLIENT, args);
    }
}
