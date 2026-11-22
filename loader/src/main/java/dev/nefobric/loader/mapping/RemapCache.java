package dev.nefobric.loader.mapping;

import dev.nefobric.loader.launch.GameInstall;
import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 重映射结果的缓存，放在 {@code <游戏目录>/.nefobric/cache/remapped/}。文件名里带输入 jar 的 sha1、映射指纹
 * 和重映射器版本，三者任何一个变了都会重新生成；旧文件不会自动删除。
 */
public final class RemapCache {
    /** 重映射逻辑有变化时加一，让旧缓存失效。 */
    static final int REMAPPER_VERSION = 1;

    private RemapCache() {
    }

    public static Path cacheDir(Path gameDir) {
        return gameDir.resolve(".nefobric").resolve("cache").resolve("remapped");
    }

    /** 原版 jar 从官方名映射到运行时命名空间。 */
    public static Path game(Path gameDir, GameInstall game, MappingStore.Loaded maps) throws IOException {
        String sha1 = MappingStore.sha1(game.gameJar()).substring(0, 12);
        String name = "minecraft-" + game.version() + "-" + game.env().name().toLowerCase(java.util.Locale.ROOT)
                + "-" + sha1 + "-" + maps.fingerprint() + "-r" + REMAPPER_VERSION + ".jar";
        Path out = cacheDir(gameDir).resolve(name);
        if (Files.isRegularFile(out)) {
            Log.debug("Using cached remapped game " + out);
            return out;
        }
        Log.info(Messages.tr("remap.game"));
        long start = System.nanoTime();
        int classes = new GameJarRemapper(maps.data(), Mappings.OFFICIAL, maps.data().runtimeNamespace())
                .remap(game.gameJar(), out);
        Log.info(Messages.tr("remap.gameDone", classes, (System.nanoTime() - start) / 1_000_000, out.getFileName()));
        return out;
    }

    /** 模组 jar 从 {@code fromNs}（例如 intermediary）映射到运行时命名空间。 */
    public static Path mod(Path gameDir, Path jar, String fromNs, MappingStore.Loaded maps) throws IOException {
        String sha1 = MappingStore.sha1(jar).substring(0, 12);
        String base = jar.getFileName().toString();
        if (base.endsWith(".jar")) {
            base = base.substring(0, base.length() - 4);
        }
        String name = base + "-" + sha1 + "-" + fromNs + "-" + maps.fingerprint() + "-r" + REMAPPER_VERSION
                + ".jar";
        Path out = cacheDir(gameDir).resolve("mods").resolve(name);
        if (Files.isRegularFile(out)) {
            return out;
        }
        Log.info(Messages.tr("remap.mod", jar.getFileName(), fromNs));
        new ModJarRemapper(maps.data(), fromNs, maps.data().runtimeNamespace()).remap(jar, out);
        return out;
    }
}
