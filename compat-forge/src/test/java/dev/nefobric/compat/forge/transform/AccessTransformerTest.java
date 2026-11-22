package dev.nefobric.compat.forge.transform;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessTransformerTest {
    private static final String FIXTURE = "dev.nefobric.compat.forge.transform.AtFixture";
    private static final String HIDDEN = FIXTURE + "$Hidden";

    @Test
    void parsesAllForms() {
        AccessTransformers.Builder b = AccessTransformers.builder().parse("""
                # comment
                public dev.nefobric.compat.forge.transform.AtFixture counter # trailing comment
                public-f dev.nefobric.compat.forge.transform.AtFixture name
                public dev.nefobric.compat.forge.transform.AtFixture secret(I)I
                protected dev.nefobric.compat.forge.transform.AtFixture <init>()V
                public dev.nefobric.compat.forge.transform.AtFixture$Hidden
                public dev.nefobric.compat.forge.transform.AtFixture$Hidden <init> ()V
                bogus dev.nefobric.Foo
                public dev.nefobric.Foo m_1_(
                \t
                """, "test");
        AccessTransformers at = b.build();
        assertEquals(6, at.entryCount());
        assertEquals(2, b.problems().size(), b.problems().toString());
        AccessTransformers.ClassChanges cc = at.forClass("dev/nefobric/compat/forge/transform/AtFixture");
        assertEquals(AccessTransformers.Access.PUBLIC, cc.field("counter").access());
        assertEquals(AccessTransformers.Final.REMOVE, cc.field("name").finality());
        assertEquals(AccessTransformers.Access.PROTECTED, cc.method("<init>", "()V").access());
        assertEquals(null, cc.method("secret", "(J)I"));
        assertEquals(AccessTransformers.Access.PUBLIC,
                at.forClass("dev/nefobric/compat/forge/transform/AtFixture$Hidden").method("<init>", "()V").access());
    }

    @Test
    void mergesToWidestAccess() {
        AccessTransformers at = AccessTransformers.builder()
                .parse("protected a.B f\npublic a.B m()V\n", "one")
                .parse("public a.B f\nprivate a.B m()V\nprotected+f a.B g\npublic-f a.B g\n", "two")
                .build();
        AccessTransformers.ClassChanges cc = at.forClass("a/B");
        assertEquals(AccessTransformers.Access.PUBLIC, cc.field("f").access());
        assertEquals(AccessTransformers.Access.PUBLIC, cc.method("m", "()V").access());
        assertEquals(AccessTransformers.Final.REMOVE, cc.field("g").finality());
    }

    @Test
    void widensMembersAndRemovesFinal() throws IOException {
        AccessTransformers at = AccessTransformers.builder().parse("""
                public dev.nefobric.compat.forge.transform.AtFixture counter
                public-f dev.nefobric.compat.forge.transform.AtFixture name
                public dev.nefobric.compat.forge.transform.AtFixture secret(I)I
                public dev.nefobric.compat.forge.transform.AtFixture <init>()V
                private dev.nefobric.compat.forge.transform.AtFixture open()V
                """, "test").build();
        ClassNode out = read(new AccessTransformerApplier(at).transform(FIXTURE, bytes(FIXTURE)));
        assertEquals(Opcodes.ACC_PUBLIC, field(out, "counter").access);
        assertEquals(Opcodes.ACC_PUBLIC, field(out, "name").access);
        assertEquals(Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, field(out, "CONSTANT").access);
        assertEquals(Opcodes.ACC_PUBLIC, method(out, "secret").access);
        assertEquals(Opcodes.ACC_PUBLIC, method(out, "<init>").access);
        // 只放宽，不收紧
        assertEquals(Opcodes.ACC_PUBLIC, method(out, "open").access);
        assertEquals(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, method(out, "helper").access);
    }

    @Test
    void callsToWidenedPrivateMethodsBecomeVirtual() throws IOException {
        // javac 11 以后同类里调用私有方法多半已经是 INVOKEVIRTUAL；老编译器和混淆器产出的是 INVOKESPECIAL
        byte[] special = withSpecialCall(bytes(FIXTURE));
        assertEquals(Opcodes.INVOKESPECIAL, calls(method(read(special), "callsSecret"), "secret").getOpcode());
        AccessTransformers at = AccessTransformers.builder()
                .parse("protected dev.nefobric.compat.forge.transform.AtFixture secret(I)I\n", "test").build();
        ClassNode out = read(new AccessTransformerApplier(at).transform(FIXTURE, special));
        assertEquals(Opcodes.INVOKEVIRTUAL, calls(method(out, "callsSecret"), "secret").getOpcode());
        // 没被放开的私有方法照旧
        ClassNode untouched = read(new AccessTransformerApplier(AccessTransformers.builder()
                .parse("public dev.nefobric.compat.forge.transform.AtFixture counter\n", "test").build())
                .transform(FIXTURE, special));
        assertEquals(Opcodes.INVOKESPECIAL, calls(method(untouched, "callsSecret"), "secret").getOpcode());
    }

    private static byte[] withSpecialCall(byte[] bytes) {
        ClassNode n = read(bytes);
        calls(method(n, "callsSecret"), "secret").setOpcode(Opcodes.INVOKESPECIAL);
        ClassWriter w = new ClassWriter(0);
        n.accept(w);
        return w.toByteArray();
    }

    private static MethodInsnNode calls(MethodNode m, String name) {
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof MethodInsnNode mi && mi.name.equals(name)) {
                return mi;
            }
        }
        throw new AssertionError("no call to " + name);
    }

    @Test
    void wildcardsCoverEveryMember() throws IOException {
        AccessTransformers at = AccessTransformers.builder().parse("""
                public dev.nefobric.compat.forge.transform.AtFixture *
                public dev.nefobric.compat.forge.transform.AtFixture *()
                """, "test").build();
        ClassNode out = read(new AccessTransformerApplier(at).transform(FIXTURE, bytes(FIXTURE)));
        for (FieldNode f : out.fields) {
            assertTrue((f.access & Opcodes.ACC_PUBLIC) != 0, f.name);
        }
        for (MethodNode m : out.methods) {
            assertTrue((m.access & Opcodes.ACC_PUBLIC) != 0, m.name);
        }
    }

    @Test
    void widensNestedClassInBothPlaces() throws IOException {
        AccessTransformers at = AccessTransformers.builder()
                .parse("public-f dev.nefobric.compat.forge.transform.AtFixture$Hidden\n", "test").build();
        AccessTransformerApplier applier = new AccessTransformerApplier(at);
        ClassNode inner = read(applier.transform(HIDDEN, bytes(HIDDEN)));
        assertTrue((inner.access & Opcodes.ACC_PUBLIC) != 0);
        assertFalse((inner.access & Opcodes.ACC_FINAL) != 0);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, innerEntry(inner, inner.name).access);
        // 外部类自己没有修改，但它的内部类表也跟着改
        ClassNode outer = read(applier.transform(FIXTURE, bytes(FIXTURE)));
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, innerEntry(outer, inner.name).access);
    }

    @Test
    void leavesOtherClassesUntouched() throws IOException {
        AccessTransformers at = AccessTransformers.builder().parse("public a.B f\n", "test").build();
        byte[] in = bytes(FIXTURE);
        assertSame(in, new AccessTransformerApplier(at).transform(FIXTURE, in));
    }

    static byte[] bytes(String className) throws IOException {
        try (InputStream in = AccessTransformerTest.class.getClassLoader()
                .getResourceAsStream(className.replace('.', '/') + ".class")) {
            return in.readAllBytes();
        }
    }

    static ClassNode read(byte[] bytes) {
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, 0);
        return n;
    }

    private static FieldNode field(ClassNode n, String name) {
        return n.fields.stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow();
    }

    private static MethodNode method(ClassNode n, String name) {
        return n.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static InnerClassNode innerEntry(ClassNode n, String name) {
        return n.innerClasses.stream().filter(i -> i.name.equals(name)).findFirst().orElseThrow();
    }
}
