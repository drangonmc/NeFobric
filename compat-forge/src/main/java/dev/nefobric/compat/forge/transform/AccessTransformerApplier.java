package dev.nefobric.compat.forge.transform;

import dev.nefobric.loader.classloading.ClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Set;

/**
 * ACCESS 阶段：把 {@link AccessTransformers} 应用到被点名的类上。改访问标志（类、内部类表、字段、方法）；
 * 私有实例方法被放开时，把同一个类里对它的 INVOKESPECIAL 改成虚调用（和 Forge 一样）。栈的形状不变，
 * 所以直接复用常量池，不重算栈帧。
 *
 * <p>类本身在 class 文件里只有 public 和包级两种；更细的访问级别记在内部类表（InnerClasses）里，两处都改。
 * 外部类的内部类表里也有它的条目，一起改，保证反射看到的一致。</p>
 */
public final class AccessTransformerApplier implements ClassTransformer {
    private final AccessTransformers ats;
    /** 自己没有修改、但内部类表里列着被改的内部类的那些外部类。 */
    private final Set<String> outers = new HashSet<>();

    public AccessTransformerApplier(AccessTransformers ats) {
        this.ats = ats;
        for (String name : ats.targetClasses()) {
            AccessTransformers.ClassChanges cc = ats.forClass(name);
            if (cc.self() == null) {
                continue;
            }
            for (int i = name.indexOf('$'); i > 0; i = name.indexOf('$', i + 1)) {
                outers.add(name.substring(0, i));
            }
        }
    }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        String internal = className.replace('.', '/');
        AccessTransformers.ClassChanges own = ats.forClass(internal);
        if (own == null && !outers.contains(internal)) {
            return bytes;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new Visitor(writer, internal, own, madeNonPrivate(reader, own)), 0);
        return writer.toByteArray();
    }

    /**
     * 原来是 private、被改成非 private 的实例方法（名字加描述符）。同一个类里对它们的调用原来是 INVOKESPECIAL
     * （不走虚分派），要改成虚调用，子类覆盖这个方法时原版代码才会调到子类的实现。
     */
    private static Set<String> madeNonPrivate(ClassReader reader, AccessTransformers.ClassChanges own) {
        if (own == null) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String n, String descriptor, String signature,
                                             String[] exceptions) {
                AccessTransformers.Change c = own.method(n, descriptor);
                if (c != null && (access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == Opcodes.ACC_PRIVATE
                        && (c.apply(access) & Opcodes.ACC_PRIVATE) == 0 && !n.startsWith("<")) {
                    out.add(n + descriptor);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return out;
    }

    private final class Visitor extends ClassVisitor {
        private final String name;
        private final AccessTransformers.ClassChanges own;
        private final Set<String> nowVirtual;
        private boolean isInterface;

        Visitor(ClassVisitor next, String name, AccessTransformers.ClassChanges own, Set<String> nowVirtual) {
            super(Opcodes.ASM9, next);
            this.name = name;
            this.own = own;
            this.nowVirtual = nowVirtual;
        }

        @Override
        public void visit(int version, int access, String n, String signature, String superName, String[] interfaces) {
            isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
            int out = access;
            if (own != null && own.self() != null) {
                out = classFileAccess(own.self().apply(access));
            }
            super.visit(version, out, n, signature, superName, interfaces);
        }

        @Override
        public void visitInnerClass(String inner, String outerName, String innerName, int access) {
            AccessTransformers.ClassChanges cc = ats.forClass(inner);
            int out = cc != null && cc.self() != null ? cc.self().apply(access) : access;
            super.visitInnerClass(inner, outerName, innerName, out);
        }

        @Override
        public FieldVisitor visitField(int access, String n, String descriptor, String signature, Object value) {
            AccessTransformers.Change c = own == null ? null : own.field(n);
            return super.visitField(c == null ? access : c.apply(access), n, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String n, String descriptor, String signature,
                                         String[] exceptions) {
            AccessTransformers.Change c = own == null ? null : own.method(n, descriptor);
            MethodVisitor mv = super.visitMethod(c == null ? access : c.apply(access), n, descriptor, signature,
                    exceptions);
            if (nowVirtual.isEmpty() || mv == null) {
                return mv;
            }
            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean itf) {
                    if (opcode == Opcodes.INVOKESPECIAL && owner.equals(name) && nowVirtual.contains(mName + mDesc)) {
                        opcode = isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
                    }
                    super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                }
            };
        }
    }

    /** class 文件的 access 只能是 public 或包级：protected 记成 public，private 记成包级（和 javac 一样）。 */
    static int classFileAccess(int access) {
        int out = access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC);
        if ((access & Opcodes.ACC_PROTECTED) != 0) {
            out |= Opcodes.ACC_PUBLIC;
        }
        return out;
    }
}
