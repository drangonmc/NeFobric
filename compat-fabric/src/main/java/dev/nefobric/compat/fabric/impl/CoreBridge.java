package dev.nefobric.compat.fabric.impl;

import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.mapping.Mappings;
import dev.nefobric.loader.metadata.ModCandidate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fabric 兼容层用到的核心接口都经过这里：映射表、游戏实例、模组实际加载的 jar、内嵌模组的父子关系。
 * 核心接口变化时只需要改这个类。
 */
public final class CoreBridge {
    private CoreBridge() {
    }

    static Mappings mappings() {
        return NeFobricLoader.isInitialized() ? NeFobricLoader.get().mappings() : Mappings.identity();
    }

    /** 运行时命名空间：有映射时是 “srg”（Mojang 类名加 SRG 成员名），未混淆的测试游戏是 “official”。 */
    public static String runtimeNamespace() {
        return mappings().runtimeNamespace();
    }

    /** 可用的命名空间。没有映射时所有名字都相同，也接受 intermediary，方便模组在测试游戏里照常调用。 */
    public static List<String> namespaces() {
        Mappings m = mappings();
        List<String> ns = new ArrayList<>(m.namespaces());
        if (m.isIdentity() && !ns.contains(Mappings.INTERMEDIARY)) {
            ns.add(Mappings.INTERMEDIARY);
        }
        return ns;
    }

    /** 类名用点分形式；找不到映射时返回原名。 */
    public static String mapClass(String fromNs, String toNs, String className) {
        return fromNs.equals(toNs) ? className : mappings().mapClass(fromNs, toNs, className);
    }

    /** owner 用点分形式、属于 fromNs；desc 也是 fromNs 的名字。找不到映射时返回原名。 */
    public static String mapField(String fromNs, String toNs, String owner, String name, String desc) {
        return fromNs.equals(toNs) ? name : mappings().mapField(fromNs, toNs, owner, name, desc);
    }

    public static String mapMethod(String fromNs, String toNs, String owner, String name, String desc) {
        return fromNs.equals(toNs) ? name : mappings().mapMethod(fromNs, toNs, owner, name, desc);
    }

    public static Object gameInstance() {
        return NeFobricLoader.isInitialized() ? NeFobricLoader.get().gameInstance() : null;
    }

    /** 模组实际加载的 jar（Fabric 模组是核心重映射后的缓存文件）。 */
    public static List<Path> rootJars(ModCandidate mod) {
        if (mod.origin() == null) {
            return List.of();
        }
        Path p = NeFobricLoader.isInitialized() ? NeFobricLoader.get().runtimePath(mod) : mod.origin();
        return List.of(p == null ? mod.origin() : p);
    }

    /** 内嵌模组的父模组。核心的 Jar-in-Jar 合并后接上。 */
    public static Optional<ModCandidate> parent(ModCandidate mod) {
        return Optional.empty();
    }

    /** 内嵌模组在父模组 jar 里的位置，例如 META-INF/jars/x.jar。 */
    public static String nestedLocation(ModCandidate mod) {
        return null;
    }
}
