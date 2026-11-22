package dev.nefobric.loader.launch;

import dev.nefobric.loader.Env;

import java.nio.file.Path;
import java.util.List;

/**
 * 找到的游戏：游戏 jar、（专用服务端才有的）从 bundler 里解出的库，以及从 {@code version.json} 读到的版本号。
 *
 * <p>{@code gameJar} 是实际加载的 jar：有映射时是重映射到运行时命名空间后的缓存文件，{@code originalGameJar}
 * 始终是原版 jar。
 */
public record GameInstall(Env env, Path gameJar, List<Path> libraries, String version, Path originalGameJar) {
    public GameInstall {
        libraries = List.copyOf(libraries);
        if (originalGameJar == null) {
            originalGameJar = gameJar;
        }
    }

    public GameInstall(Env env, Path gameJar, List<Path> libraries, String version) {
        this(env, gameJar, libraries, version, gameJar);
    }

    /** 换成重映射后的 jar，原版 jar 保留在 {@link #originalGameJar()}。 */
    public GameInstall withGameJar(Path runtimeJar) {
        return new GameInstall(env, runtimeJar, libraries, version, originalGameJar);
    }
}
