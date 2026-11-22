package dev.nefobric.compat.forge.transform;

import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Forge 访问变换（{@code META-INF/accesstransformer.cfg}）合并后的结果。格式和 Forge 47 一样，每行一条：
 * <pre>
 * public net.minecraft.world.level.Level f_46443_          # 字段
 * public-f net.minecraft.world.item.Item m_41462_(I)Z      # 方法（名字和描述符之间没有空格）
 * protected net.minecraft.client.gui.Gui$HeartType         # 类
 * public net.minecraft.Foo *                                # 所有字段
 * public net.minecraft.Foo *()                              # 所有方法
 * </pre>
 * 修饰符是 {@code public}、{@code protected}、{@code default}、{@code private}，可以带 {@code -f}（去掉 final）
 * 或 {@code +f}（加上 final）。名字用运行时命名（Mojang 类名、SRG 成员名），和 Forge 正式环境一样，不需要重映射。
 *
 * <p>多个模组改同一个目标时，访问级别取最宽的；只会放宽，不会收紧。一个要去掉 final、另一个要加上 final 时按去掉处理。</p>
 */
public final class AccessTransformers {
    /** 访问级别，按从宽到窄排列。 */
    public enum Access {
        PUBLIC(Opcodes.ACC_PUBLIC), PROTECTED(Opcodes.ACC_PROTECTED), DEFAULT(0), PRIVATE(Opcodes.ACC_PRIVATE);

        static final int MASK = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE;
        final int flag;

        Access(int flag) {
            this.flag = flag;
        }

        static Access of(int access) {
            if ((access & Opcodes.ACC_PUBLIC) != 0) {
                return PUBLIC;
            } else if ((access & Opcodes.ACC_PROTECTED) != 0) {
                return PROTECTED;
            } else if ((access & Opcodes.ACC_PRIVATE) != 0) {
                return PRIVATE;
            }
            return DEFAULT;
        }
    }

    /** 对 final 的处理。 */
    public enum Final {
        LEAVE, MAKE, REMOVE;

        Final merge(Final other) {
            if (this == other || other == LEAVE) {
                return this;
            }
            if (this == LEAVE) {
                return other;
            }
            return REMOVE;
        }
    }

    /** 对一个目标的修改。 */
    public record Change(Access access, Final finality) {
        Change merge(Change other) {
            if (other == null) {
                return this;
            }
            Access a = access.ordinal() <= other.access.ordinal() ? access : other.access;
            return new Change(a, finality.merge(other.finality));
        }

        /** 把修改应用到成员或类的访问标志上：只放宽，不收紧。 */
        public int apply(int flags) {
            int out = flags;
            if (access.ordinal() < Access.of(flags).ordinal()) {
                out = (out & ~Access.MASK) | access.flag;
            }
            if (finality == Final.REMOVE) {
                out &= ~Opcodes.ACC_FINAL;
            } else if (finality == Final.MAKE) {
                out |= Opcodes.ACC_FINAL;
            }
            return out;
        }
    }

    /** 一个类上的全部修改。成员的键：字段是名字，方法是名字加描述符。 */
    public static final class ClassChanges {
        Change self;
        Change allFields;
        Change allMethods;
        final Map<String, Change> fields = new HashMap<>();
        final Map<String, Change> methods = new HashMap<>();

        public Change self() {
            return self;
        }

        public Change field(String name) {
            Change c = fields.get(name);
            return c == null ? allFields : c.merge(allFields);
        }

        public Change method(String name, String desc) {
            Change c = methods.get(name + desc);
            if ("<clinit>".equals(name)) {
                return c;
            }
            return c == null ? allMethods : c.merge(allMethods);
        }

        boolean hasMembers() {
            return allFields != null || allMethods != null || !fields.isEmpty() || !methods.isEmpty();
        }
    }

    private final Map<String, ClassChanges> classes;
    private final int entries;

    private AccessTransformers(Map<String, ClassChanges> classes, int entries) {
        this.classes = classes;
        this.entries = entries;
    }

    public static AccessTransformers empty() {
        return new AccessTransformers(Map.of(), 0);
    }

    /** 内部名（{@code net/minecraft/Foo$Bar}）对应的修改；没有时返回 {@code null}。 */
    public ClassChanges forClass(String internalName) {
        return classes.get(internalName);
    }

    public Set<String> targetClasses() {
        return Collections.unmodifiableSet(classes.keySet());
    }

    public boolean isEmpty() {
        return classes.isEmpty();
    }

    /** 合并后的条目数（解析成功的行数）。 */
    public int entryCount() {
        return entries;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, ClassChanges> classes = new HashMap<>();
        private final List<String> problems = new ArrayList<>();
        private int entries;

        /**
         * 解析一个 AT 文件，并入已有内容。写错的行跳过，原因记在 {@link #problems()} 里。
         *
         * @param origin 出处，用于提示，例如模组 id
         */
        public Builder parse(String text, String origin) {
            String[] lines = text.split("\\R");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int hash = line.indexOf('#');
                if (hash >= 0) {
                    line = line.substring(0, hash);
                }
                line = line.strip();
                if (line.isEmpty()) {
                    continue;
                }
                String error = parseLine(line);
                if (error != null) {
                    problems.add(origin + " line " + (i + 1) + ": " + error + ": " + line);
                } else {
                    entries++;
                }
            }
            return this;
        }

        private String parseLine(String line) {
            String[] t = line.split("\\s+");
            if (t.length < 2) {
                return "missing class name";
            }
            Change change = parseModifier(t[0]);
            if (change == null) {
                return "unknown modifier " + t[0];
            }
            String owner = t[1].replace('.', '/');
            if (owner.isEmpty() || owner.contains("(")) {
                return "bad class name";
            }
            ClassChanges cc = classes.computeIfAbsent(owner, k -> new ClassChanges());
            if (t.length == 2) {
                cc.self = change.merge(cc.self);
                return null;
            }
            String member = t[2];
            // 少数文件在方法名和描述符之间留了空格
            if (t.length >= 4 && t[3].startsWith("(") && !member.contains("(")) {
                member = member + t[3];
            } else if (t.length > 3) {
                return "unexpected text after member";
            }
            if (member.equals("*")) {
                cc.allFields = change.merge(cc.allFields);
            } else if (member.equals("*()")) {
                cc.allMethods = change.merge(cc.allMethods);
            } else if (member.indexOf('(') > 0) {
                int paren = member.indexOf('(');
                String desc = member.substring(paren);
                if (desc.indexOf(')') < 0 || desc.endsWith(")")) {
                    return "bad method descriptor";
                }
                cc.methods.merge(member.substring(0, paren) + desc, change, Change::merge);
            } else if (member.indexOf('(') < 0) {
                cc.fields.merge(member, change, Change::merge);
            } else {
                return "bad member";
            }
            return null;
        }

        private static Change parseModifier(String s) {
            Final f = Final.LEAVE;
            String name = s;
            if (s.endsWith("-f")) {
                f = Final.REMOVE;
                name = s.substring(0, s.length() - 2);
            } else if (s.endsWith("+f")) {
                f = Final.MAKE;
                name = s.substring(0, s.length() - 2);
            }
            Access a = switch (name) {
                case "public" -> Access.PUBLIC;
                case "protected" -> Access.PROTECTED;
                case "default" -> Access.DEFAULT;
                case "private" -> Access.PRIVATE;
                default -> null;
            };
            return a == null ? null : new Change(a, f);
        }

        public List<String> problems() {
            return List.copyOf(problems);
        }

        public AccessTransformers build() {
            Map<String, ClassChanges> copy = new HashMap<>(classes);
            return new AccessTransformers(copy, entries);
        }
    }

    /** 测试和诊断用：某个类有没有成员级的修改。 */
    boolean hasMemberChanges(String internalName) {
        ClassChanges cc = classes.get(internalName);
        return cc != null && cc.hasMembers();
    }
}
