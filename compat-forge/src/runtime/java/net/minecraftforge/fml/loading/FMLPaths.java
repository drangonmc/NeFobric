package net.minecraftforge.fml.loading;

import dev.nefobric.forge.fml.ForgeEnvironment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 游戏目录下的标准路径。 */
public enum FMLPaths {
    GAMEDIR(),
    MODSDIR("mods"),
    CONFIGDIR("config"),
    FMLCONFIG(false, CONFIGDIR, "fml.toml");

    private final Path relativePath;
    private final boolean isDirectory;
    private Path absolutePath;

    FMLPaths() {
        this("");
    }

    FMLPaths(String... path) {
        relativePath = computePath(path);
        isDirectory = true;
    }

    FMLPaths(boolean isDir, FMLPaths parent, String... path) {
        relativePath = parent.relativePath.resolve(computePath(path));
        isDirectory = isDir;
    }

    private static Path computePath(String... path) {
        return Paths.get(path[0], java.util.Arrays.copyOfRange(path, 1, path.length));
    }

    public static void loadAbsolutePaths(Path rootPath) {
        for (FMLPaths p : values()) {
            p.absolutePath = rootPath.resolve(p.relativePath).toAbsolutePath().normalize();
            if (p.isDirectory) {
                getOrCreateGameRelativePath(p.absolutePath);
            }
        }
    }

    public static Path getOrCreateGameRelativePath(Path path) {
        Path gameFolderPath = ForgeEnvironment.gameDir().resolve(path);
        if (!Files.isDirectory(gameFolderPath)) {
            try {
                Files.createDirectories(gameFolderPath);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return gameFolderPath;
    }

    public Path relative() {
        return relativePath;
    }

    public Path get() {
        if (absolutePath == null) {
            loadAbsolutePaths(ForgeEnvironment.gameDir());
        }
        return absolutePath;
    }
}
