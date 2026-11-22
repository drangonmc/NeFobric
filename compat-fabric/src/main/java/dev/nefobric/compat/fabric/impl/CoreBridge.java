package dev.nefobric.compat.fabric.impl;

import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.metadata.ModCandidate;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Fabric 兼容层用到的所有“核心新接口”都经过这里：映射表、游戏实例、内嵌模组的父子关系、模组的真实根路径。
 * 核心的对应接口（见与核心线程约定的 Mappings、gameInstance()、parent()/children()/rootPaths()）合并之后，
 * 只需要改这个类。在那之前用保守的退路：没有映射（运行时名就是输入名）、游戏实例为空、没有内嵌关系、根路径就是模组 jar。
 */
public final class CoreBridge {
    private CoreBridge() {
    }

    /** 运行时命名空间的名字；核心按架构文档使用 Mojang 类名加 SRG 成员名，记作 “srg”。 */
    public static String runtimeNamespace() {
        return "srg";
    }

    public static List<String> namespaces() {
        return List.of("official", "intermediary", runtimeNamespace());
    }

    /** 类名用点分形式；找不到时返回 {@code null}。 */
    public static String mapClass(String fromNs, String toNs, String className) {
        return fromNs.equals(toNs) ? className : null;
    }

    /** owner 用点分形式、属于 fromNs；desc 也是 fromNs 的名字。找不到时返回 {@code null}。 */
    public static String mapField(String fromNs, String toNs, String owner, String name, String desc) {
        return fromNs.equals(toNs) ? name : null;
    }

    public static String mapMethod(String fromNs, String toNs, String owner, String name, String desc) {
        return fromNs.equals(toNs) ? name : null;
    }

    public static Object gameInstance() {
        return null;
    }

    /** 模组运行时使用的 jar（可能是核心重映射或解包后的文件）。 */
    public static List<Path> rootJars(ModCandidate mod) {
        return mod.origin() == null ? List.of() : List.of(mod.origin());
    }

    /** 内嵌模组的父模组。 */
    public static Optional<ModCandidate> parent(ModCandidate mod) {
        return Optional.empty();
    }

    /** 内嵌模组在父模组 jar 里的位置，例如 META-INF/jars/x.jar。 */
    public static String nestedLocation(ModCandidate mod) {
        return null;
    }

    public static NeFobricLoader loader() {
        return NeFobricLoader.get();
    }
}
