package dev.nefobric.loader.discovery;

import dev.nefobric.loader.metadata.ModCandidate;

import java.nio.file.Path;

/**
 * 模组内嵌的、本身不是模组的 jar（例如 Forge 模组 jarjar 里的普通库）。只有父模组被选中时才加进类加载器。
 *
 * @param path         解到缓存里的文件
 * @param parent       内嵌它的模组
 * @param pathInParent 在父模组 jar 里的路径
 */
public record NestedLibrary(Path path, ModCandidate parent, String pathInParent) {
}
