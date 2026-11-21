package dev.nefobric.loader.launch;

import dev.nefobric.loader.Env;

import java.nio.file.Path;
import java.util.List;

/**
 * 找到的游戏：游戏 jar、（专用服务端才有的）从 bundler 里解出的库，以及从 {@code version.json} 读到的版本号。
 */
public record GameInstall(Env env, Path gameJar, List<Path> libraries, String version) {
    public GameInstall {
        libraries = List.copyOf(libraries);
    }
}
