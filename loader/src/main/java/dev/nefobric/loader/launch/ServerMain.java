package dev.nefobric.loader.launch;

import dev.nefobric.loader.Env;

/**
 * 专用服务端入口：{@code java -jar nefobric-loader.jar nogui}，在放有原版 {@code server.jar} 的服务器目录里运行。
 */
public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] args) {
        Launcher.main(Env.SERVER, args);
    }
}
