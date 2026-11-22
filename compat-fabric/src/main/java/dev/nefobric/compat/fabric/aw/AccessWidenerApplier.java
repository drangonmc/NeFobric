package dev.nefobric.compat.fabric.aw;

import dev.nefobric.compat.fabric.impl.CoreBridge;
import dev.nefobric.loader.classloading.ClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 把所有 Fabric 模组的 access widener 合并后应用到游戏类上，挂在类变换流水线的 ACCESS 阶段（Mixin 之前，
 * 与 Fabric Loader 的顺序相同）。
 *
 * <p>语义逐条照搬 Fabric 的 access-widener 库（{@code AccessWidener}、{@code AccessWidenerClassVisitor}）
 * 和 Fabric Loader 的 {@code FabricTransformer}：
 * <ul>
 *   <li>只处理游戏类：条目的类名按文件里的命名空间判断，{@code net/minecraft/}、{@code com/mojang/blaze3d/}
 *       或没有包名的类才算；其他条目（比如 brigadier 的类）和 Fabric 一样被忽略；</li>
 *   <li>同一个类/成员的多个条目（可以来自不同模组）合并，例如 accessible 加 extendable；</li>
 *   <li>要改写的类是条目的所属类和它的所有外部类（按 {@code $} 切），这样外部类 InnerClasses 里的条目也会改；</li>
 *   <li>类：accessible → public；extendable → public 并去掉 final，另外去掉 PermittedSubclasses（sealed 失效）；</li>
 *   <li>方法：accessible → public，原来是 private 的实例方法（非构造函数、所属类不是接口）加 final；
 *       extendable → protected（public 保持 public）并去掉 final；两者都有 → public 并去掉 final；
 *       本类里对这些方法的 invokespecial 改成 invokevirtual（接口方法改成 invokeinterface）；</li>
 *   <li>字段：accessible → public；mutable → 去掉 final，但接口里的 static 字段不动；</li>
 *   <li>transitive 在运行时没有区别。</li>
 * </ul>
 */
public final class AccessWidenerApplier implements ClassTransformer {
    static final int ACCESSIBLE = 1;
    static final int EXTENDABLE = 2;
    static final int MUTABLE = 4;
    private static final int ASM = Opcodes.ASM9;

    /** 键是内部类名（运行时命名空间）。 */
    private final Map<String, Integer> classAccess;
    /** 键是 {@code owner name desc}。 */
    private final Map<String, Integer> methodAccess;
    private final Map<String, Integer> fieldAccess;
    /** 类里有被放宽的方法：这些类要改写 invokespecial。 */
    private final Set<String> ownersWithMethods;
    /** 需要改写的类，二进制名（点号分隔），与 {@link #transform} 收到的类名同一形式。 */
    private final Set<String> targets;

    private AccessWidenerApplier(Builder b) {
        this.classAccess = Map.copyOf(b.classAccess);
        this.methodAccess = Map.copyOf(b.methodAccess);
        this.fieldAccess = Map.copyOf(b.fieldAccess);
        this.ownersWithMethods = Set.copyOf(b.ownersWithMethods);
        this.targets = Set.copyOf(b.targets);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 没有任何条目时不需要挂到流水线上。 */
    public boolean isEmpty() {
        return targets.isEmpty();
    }

    /** 需要改写的类（二进制名）。 */
    public Set<String> targets() {
        return Collections.unmodifiableSet(targets);
    }

    /** 与 Fabric Loader 的 {@code FabricTransformer} 判断游戏类的规则相同，用文件里的命名空间（intermediary）的类名判断。 */
    static boolean isMinecraftClass(String internalName) {
        return internalName.startsWith("net/minecraft/") || internalName.startsWith("com/mojang/blaze3d/")
                || internalName.indexOf('/') < 0;
    }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        if (!targets.contains(className)) {
            return bytes;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new WideningVisitor(writer), 0);
        return writer.toByteArray();
    }

    // ---- 与 access-widener 库相同的访问标志运算 ----

    static int makePublic(int access) {
        return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
    }

    static int makeProtected(int access) {
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            return access;
        }
        return (access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PROTECTED;
    }

    static int makeFinalIfPrivate(int access, String name, int ownerAccess) {
        if (name.equals("<init>")) {
            return access;
        }
        if ((ownerAccess & Opcodes.ACC_INTERFACE) != 0 || (access & Opcodes.ACC_STATIC) != 0) {
            return access;
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            return access | Opcodes.ACC_FINAL;
        }
        return access;
    }

    static int removeFinal(int access) {
        return access & ~Opcodes.ACC_FINAL;
    }

    static int applyClass(int flags, int access) {
        if (flags == 0) {
            return access;
        }
        if ((flags & EXTENDABLE) != 0) {
            return makePublic(removeFinal(access));
        }
        return makePublic(access);
    }

    static int applyMethod(int flags, int access, String name, int ownerAccess) {
        boolean accessible = (flags & ACCESSIBLE) != 0;
        boolean extendable = (flags & EXTENDABLE) != 0;
        if (accessible && extendable) {
            return makePublic(removeFinal(access));
        }
        if (accessible) {
            return makePublic(makeFinalIfPrivate(access, name, ownerAccess));
        }
        if (extendable) {
            return makeProtected(removeFinal(access));
        }
        return access;
    }

    static int applyField(int flags, int access, int ownerAccess) {
        boolean accessible = (flags & ACCESSIBLE) != 0;
        boolean mutable = (flags & MUTABLE) != 0;
        boolean staticInInterface = (ownerAccess & Opcodes.ACC_INTERFACE) != 0 && (access & Opcodes.ACC_STATIC) != 0;
        if (accessible && mutable) {
            return staticInInterface ? makePublic(access) : makePublic(removeFinal(access));
        }
        if (accessible) {
            return makePublic(access);
        }
        if (mutable) {
            return staticInInterface ? access : removeFinal(access);
        }
        return access;
    }

    private static String key(String owner, String name, String desc) {
        return owner + ' ' + name + ' ' + desc;
    }

    private final class WideningVisitor extends ClassVisitor {
        private String className;
        private int ownerAccess;

        WideningVisitor(ClassVisitor next) {
            super(ASM, next);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            className = name;
            ownerAccess = access;
            super.visit(version, applyClass(classAccess.getOrDefault(name, 0), access), name, signature, superName, interfaces);
        }

        @Override
        public void visitPermittedSubclass(String permittedSubclass) {
            if ((classAccess.getOrDefault(className, 0) & EXTENDABLE) != 0) {
                return;
            }
            super.visitPermittedSubclass(permittedSubclass);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            super.visitInnerClass(name, outerName, innerName, applyClass(classAccess.getOrDefault(name, 0), access));
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            int flags = fieldAccess.getOrDefault(key(className, name, descriptor), 0);
            return super.visitField(applyField(flags, access, ownerAccess), name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            int flags = methodAccess.getOrDefault(key(className, name, descriptor), 0);
            MethodVisitor mv = super.visitMethod(applyMethod(flags, access, name, ownerAccess), name, descriptor, signature, exceptions);
            if (mv == null || !ownersWithMethods.contains(className)) {
                return mv;
            }
            return new MethodVisitor(ASM, mv) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean isInterface) {
                    if (opcode == Opcodes.INVOKESPECIAL && owner.equals(className) && !mName.equals("<init>")
                            && methodAccess.containsKey(key(owner, mName, mDesc))) {
                        opcode = isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
                    }
                    super.visitMethodInsn(opcode, owner, mName, mDesc, isInterface);
                }
            };
        }
    }

    /** 收集多个模组的 access widener。 */
    public static final class Builder {
        private final Map<String, Integer> classAccess = new HashMap<>();
        private final Map<String, Integer> methodAccess = new HashMap<>();
        private final Map<String, Integer> fieldAccess = new HashMap<>();
        private final Set<String> ownersWithMethods = new HashSet<>();
        private final Set<String> targets = new HashSet<>();

        private Builder() {
        }

        /**
         * 加入一个按文件原样读出的 access widener（命名空间是文件头写的那个，一般是 intermediary）：
         * 先按原命名空间筛掉非游戏类的条目，再换算到运行时命名空间。
         *
         * @throws IllegalArgumentException 命名空间不是映射表里有的那几个
         */
        public Builder add(AccessWidener aw) {
            if (!CoreBridge.namespaces().contains(aw.namespace())) {
                throw new IllegalArgumentException("Namespace (" + aw.namespace()
                        + ") does not match current runtime namespace (" + CoreBridge.runtimeNamespace()
                        + ") and cannot be remapped to it");
            }
            AccessWidener gameOnly = new AccessWidener(aw.namespace(), aw.version(), aw.entries().stream()
                    .filter(e -> isMinecraftClass(e.owner()))
                    .toList());
            return addRuntime(AccessWidenerRemapper.toRuntime(gameOnly));
        }

        /** 加入已经是运行时命名空间、已经筛过游戏类的条目。 */
        Builder addRuntime(AccessWidener aw) {
            for (AccessWidener.Entry e : aw.entries()) {
                int bit = switch (e.access()) {
                    case ACCESSIBLE -> ACCESSIBLE;
                    case EXTENDABLE -> EXTENDABLE;
                    case MUTABLE -> MUTABLE;
                };
                switch (e.target()) {
                    case CLASS -> classAccess.merge(e.owner(), bit, (a, b) -> a | b);
                    case METHOD -> {
                        methodAccess.merge(key(e.owner(), e.name(), e.desc()), bit, (a, b) -> a | b);
                        ownersWithMethods.add(e.owner());
                    }
                    case FIELD -> fieldAccess.merge(key(e.owner(), e.name(), e.desc()), bit, (a, b) -> a | b);
                }
                addTargets(e.owner());
            }
            return this;
        }

        private void addTargets(String internalName) {
            String cls = internalName.replace('/', '.');
            targets.add(cls);
            int i;
            while ((i = cls.lastIndexOf('$')) > 0) {
                cls = cls.substring(0, i);
                targets.add(cls);
            }
        }

        public AccessWidenerApplier build() {
            return new AccessWidenerApplier(this);
        }
    }
}
