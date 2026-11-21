package dev.nefobric.loader.launch;

import dev.nefobric.loader.Env;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 从启动参数里读 NeFobric 需要的少量信息。参数本身原样传给游戏，NeFobric 自己的设置都走 {@code -Dnefobric.*}。
 */
public record LaunchArgs(Env env, Path gameDir, String[] raw) {

    public static LaunchArgs parse(Env env, String[] args) {
        Path gameDir = null;
        if (env == Env.CLIENT) {
            for (int i = 0; i < args.length - 1; i++) {
                if ("--gameDir".equals(args[i])) {
                    gameDir = Paths.get(args[i + 1]);
                }
            }
        }
        if (gameDir == null) {
            // 专用服务端以当前目录为根，客户端没给 --gameDir 时原版也用当前目录
            gameDir = Paths.get("");
        }
        return new LaunchArgs(env, gameDir.toAbsolutePath().normalize(), args.clone());
    }

    public Path modsDir() {
        String override = System.getProperty("nefobric.modsDir");
        return override != null && !override.isBlank()
                ? Paths.get(override).toAbsolutePath().normalize()
                : gameDir.resolve("mods");
    }

    public Path configDir() {
        return gameDir.resolve("config");
    }
}
