package net.fabricmc.loader.api;

import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 一个已加载的模组：元数据、jar 里的文件、来源和内嵌关系。 */
public interface ModContainer {
    ModMetadata getMetadata();

    /** 模组的根目录（jar 的 zip 文件系统根），可以用来读模组自带的文件。 */
    List<Path> getRootPaths();

    default Optional<Path> findPath(String file) {
        for (Path root : getRootPaths()) {
            Path path = root.resolve(file.replace("/", root.getFileSystem().getSeparator()));
            if (Files.exists(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    ModOrigin getOrigin();

    Optional<ModContainer> getContainingMod();

    Collection<ModContainer> getContainedMods();

    @Deprecated
    Path getRootPath();

    @Deprecated
    default Path getPath(String file) {
        Optional<Path> res = findPath(file);
        if (res.isPresent()) {
            return res.get();
        }
        List<Path> roots = getRootPaths();
        if (!roots.isEmpty()) {
            Path root = roots.get(0);
            return root.resolve(file.replace("/", root.getFileSystem().getSeparator()));
        }
        return getRootPath().resolve(file);
    }
}
