package dev.nefobric.compat.fabric.aw;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.*;

class AccessWidenerApplierTest {
    private static final String TARGET = "net/minecraft/class_1";

    /**
     * {@code final class class_1}，带私有方法 method_1 和调用它的 call()（invokespecial），
     * 私有 final 字段，私有静态内部类 class_1$class_2。
     */
    private static byte[] targetClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V17, ACC_FINAL | ACC_SUPER, TARGET, null, "java/lang/Object", null);
        cw.visitInnerClass(TARGET + "$class_2", TARGET, "class_2", ACC_PRIVATE | ACC_STATIC);
        cw.visitField(ACC_PRIVATE | ACC_FINAL, "field_1", "I", null, null).visitEnd();
        cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "field_2", "I", null, 5).visitEnd();
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_PRIVATE, "method_1", "()I", null, null);
        mv.visitCode();
        mv.visitInsn(ICONST_1);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_PRIVATE, "method_2", "()I", null, null);
        mv.visitCode();
        mv.visitInsn(ICONST_2);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, "method_3", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_PUBLIC, "call", "()I", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, TARGET, "method_1", "()I", false);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** 继承 class_1 并覆盖 method_1 返回 42 的子类：只有放宽成功才能加载并生效。 */
    private static byte[] subclass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V17, ACC_PUBLIC | ACC_SUPER, "net/minecraft/Sub", null, TARGET, null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, TARGET, "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_PROTECTED, "method_1", "()I", null, null);
        mv.visitCode();
        mv.visitIntInsn(BIPUSH, 42);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static ClassNode node(byte[] b) {
        ClassNode n = new ClassNode();
        new ClassReader(b).accept(n, 0);
        return n;
    }

    private static MethodNode method(ClassNode n, String name) {
        return n.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static FieldNode field(ClassNode n, String name) {
        return n.fields.stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow();
    }

    private static AccessWidenerApplier applier(String text) throws Exception {
        return AccessWidenerApplier.builder().add(AccessWidenerReader.read(text)).build();
    }

    @Test
    void extendableClassAndMethodCanBeSubclassedAndOverridden() throws Exception {
        AccessWidenerApplier aw = applier("""
                accessWidener v2 intermediary
                extendable class net/minecraft/class_1
                extendable method net/minecraft/class_1 method_1 ()I
                """);
        byte[] out = aw.transform("net.minecraft.class_1", targetClass());
        ClassNode n = node(out);
        assertEquals(ACC_PUBLIC | ACC_SUPER, n.access);
        assertEquals(ACC_PROTECTED, method(n, "method_1").access);
        // call() 里的 invokespecial 改成 invokevirtual，子类覆盖才会生效
        MethodInsnNode insn = null;
        for (AbstractInsnNode i : method(n, "call").instructions) {
            if (i instanceof MethodInsnNode m) {
                insn = m;
            }
        }
        assertEquals(INVOKEVIRTUAL, insn.getOpcode());

        ClassLoader cl = new ClassLoader(null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] b = switch (name) {
                    case "net.minecraft.class_1" -> out;
                    case "net.minecraft.Sub" -> subclass();
                    default -> throw new ClassNotFoundException(name);
                };
                return defineClass(name, b, 0, b.length);
            }
        };
        Object sub = cl.loadClass("net.minecraft.Sub").getConstructor().newInstance();
        assertEquals(42, cl.loadClass("net.minecraft.class_1").getMethod("call").invoke(sub));
    }

    @Test
    void accessibleMethodBecomesPublicFinalWhenPrivate() throws Exception {
        AccessWidenerApplier aw = applier("""
                accessWidener v1 intermediary
                accessible method net/minecraft/class_1 method_1 ()I
                accessible method net/minecraft/class_1 method_3 ()V
                accessible method net/minecraft/class_1 <init> ()V
                """);
        ClassNode n = node(aw.transform("net.minecraft.class_1", targetClass()));
        assertEquals(ACC_PUBLIC | ACC_FINAL, method(n, "method_1").access);
        // static 方法不加 final
        assertEquals(ACC_PUBLIC | ACC_STATIC, method(n, "method_3").access);
        assertEquals(ACC_PUBLIC, method(n, "<init>").access);
        // 只是 accessible 的类不变
        assertEquals(ACC_FINAL | ACC_SUPER, n.access);
        // 未列出的方法不变
        assertEquals(ACC_PRIVATE, method(n, "method_2").access);
    }

    @Test
    void entriesFromSeveralModsMerge() throws Exception {
        AccessWidenerApplier aw = AccessWidenerApplier.builder()
                .add(AccessWidenerReader.read("""
                        accessWidener v2 intermediary
                        accessible method net/minecraft/class_1 method_1 ()I
                        accessible field net/minecraft/class_1 field_1 I
                        """))
                .add(AccessWidenerReader.read("""
                        accessWidener v2 intermediary
                        transitive-extendable method net/minecraft/class_1 method_1 ()I
                        mutable field net/minecraft/class_1 field_1 I
                        """))
                .build();
        ClassNode n = node(aw.transform("net.minecraft.class_1", targetClass()));
        // accessible + extendable：public 且不 final
        assertEquals(ACC_PUBLIC, method(n, "method_1").access);
        // accessible + mutable：public 且不 final
        assertEquals(ACC_PUBLIC, field(n, "field_1").access);
    }

    @Test
    void fieldsAndInnerClasses() throws Exception {
        AccessWidenerApplier aw = applier("""
                accessWidener v2 intermediary
                mutable field net/minecraft/class_1 field_1 I
                accessible field net/minecraft/class_1 field_2 I
                accessible class net/minecraft/class_1$class_2
                """);
        // 内部类条目让外部类也成为改写目标
        assertTrue(aw.targets().containsAll(Set.of("net.minecraft.class_1", "net.minecraft.class_1$class_2")));
        ClassNode n = node(aw.transform("net.minecraft.class_1", targetClass()));
        assertEquals(ACC_PRIVATE, field(n, "field_1").access);
        assertEquals(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, field(n, "field_2").access);
        InnerClassNode inner = n.innerClasses.get(0);
        assertEquals(ACC_PUBLIC | ACC_STATIC, inner.access);
    }

    @Test
    void staticInterfaceFieldsStayFinal() {
        int itf = ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT;
        int f = ACC_PUBLIC | ACC_STATIC | ACC_FINAL;
        assertEquals(f, AccessWidenerApplier.applyField(AccessWidenerApplier.MUTABLE, f, itf));
        assertEquals(ACC_PRIVATE, AccessWidenerApplier.applyField(AccessWidenerApplier.MUTABLE, ACC_PRIVATE | ACC_FINAL, ACC_PUBLIC));
        // 接口里的私有实例方法 accessible 时不加 final
        assertEquals(ACC_PUBLIC, AccessWidenerApplier.applyMethod(AccessWidenerApplier.ACCESSIBLE, ACC_PRIVATE, "m", itf));
        // extendable 的 public 方法保持 public
        assertEquals(ACC_PUBLIC, AccessWidenerApplier.applyMethod(AccessWidenerApplier.EXTENDABLE, ACC_PUBLIC | ACC_FINAL, "m", ACC_PUBLIC));
    }

    @Test
    void extendableSealedClassLosesPermittedSubclasses() throws Exception {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(V17, ACC_PUBLIC | ACC_ABSTRACT | ACC_SUPER, "net/minecraft/class_9", null, "java/lang/Object", null);
        cw.visitPermittedSubclass("net/minecraft/class_10");
        cw.visitEnd();
        AccessWidenerApplier aw = applier("""
                accessWidener v2 intermediary
                extendable class net/minecraft/class_9
                """);
        ClassNode n = node(aw.transform("net.minecraft.class_9", cw.toByteArray()));
        assertNull(n.permittedSubclasses);
    }

    @Test
    void nonGameClassesAndUnlistedClassesAreUntouched() throws Exception {
        AccessWidenerApplier aw = applier("""
                accessWidener v2 intermediary
                accessible class com/mojang/brigadier/CommandDispatcher
                accessible class com/mojang/blaze3d/systems/RenderSystem
                accessible class net/minecraft/class_5$class_6$class_7
                """);
        assertFalse(aw.targets().contains("com.mojang.brigadier.CommandDispatcher"));
        assertTrue(aw.targets().containsAll(Set.of("com.mojang.blaze3d.systems.RenderSystem",
                "net.minecraft.class_5", "net.minecraft.class_5$class_6", "net.minecraft.class_5$class_6$class_7")));
        byte[] in = targetClass();
        assertSame(in, aw.transform("net.minecraft.class_1", in));
    }

    @Test
    void emptyAndUnknownNamespace() throws Exception {
        assertTrue(AccessWidenerApplier.builder().build().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> applier("""
                accessWidener v2 named
                accessible class net/minecraft/client/Minecraft
                """));
    }
}
