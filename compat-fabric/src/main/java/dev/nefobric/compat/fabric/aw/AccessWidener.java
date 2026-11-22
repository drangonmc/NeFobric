package dev.nefobric.compat.fabric.aw;

import java.util.List;

/**
 * 一个解析好的 access widener 文件。类名是内部形式（{@code net/minecraft/class_1234}）。
 *
 * <p>应用到类上的语义见 {@link AccessWidenerApplier}（与 Fabric 的 access-widener 库相同）。
 * transitive-* 在运行时与非 transitive 相同。
 */
public record AccessWidener(String namespace, int version, List<Entry> entries) {

    public enum Target { CLASS, METHOD, FIELD }

    public enum Access { ACCESSIBLE, EXTENDABLE, MUTABLE }

    /** {@code name} 和 {@code desc} 只对方法和字段有意义。 */
    public record Entry(Target target, Access access, boolean transitive, String owner, String name, String desc) {
    }

    public AccessWidener {
        entries = List.copyOf(entries);
    }
}
