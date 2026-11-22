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
 * ACCESS 阶段：把 {@link AccessTransformers} 应用到被点名的类上。只改访问标志（类、内部类表、字段、方法），
 * 不动字节码，所以直接复用常量池，不重算栈帧。
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
        reader.accept(new Visitor(writer, internal, own), 0);
        return writer.toByteArray();
    }

    private final class Visitor extends ClassVisitor {
        private final String name;
        private final AccessTransformers.ClassChanges own;

        Visitor(ClassVisitor next, String name, AccessTransformers.ClassChanges own) {
            super(Opcodes.ASM9, next);
            this.name = name;
            this.own = own;
        }

        @Override
        public void visit(int version, int access, String n, String signature, String superName, String[] interfaces) {
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
            return super.visitMethod(c == null ? access : c.apply(access), n, descriptor, signature, exceptions);
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
