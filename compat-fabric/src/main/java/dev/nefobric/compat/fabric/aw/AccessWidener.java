package dev.nefobric.compat.fabric.aw;

import java.util.List;

/**
 * 一个解析好的 access widener 文件。类名是内部形式（{@code net/minecraft/class_1234}）。
 *
 * <p>Fabric 的语义（核心执行时需要照做，与 access-widener 库一致）：
 * <ul>
 *   <li>类 accessible：改成 public；extendable：改成 public 并去掉 final；其他类里指向它的 InnerClasses 条目同样修改；</li>
 *   <li>方法 accessible：改成 public；原来是 private 且不是构造函数、不是 static 时加上 final（避免变成可覆盖的虚方法），
 *       并把本类里对它的 invokespecial 改成 invokevirtual；extendable：private 改成 protected（public/protected 保持）并去掉 final；</li>
 *   <li>字段 accessible：改成 public；mutable：去掉 final。</li>
 * </ul>
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
