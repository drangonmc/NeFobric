package cpw.mods.modlauncher.api;

import java.nio.file.Path;

/** 带名字的一组路径（ModLauncher 交给启动插件的特殊路径）。 */
public record NamedPath(String name, Path... paths) {
}
