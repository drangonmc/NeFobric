package dev.nefobric.loader.mapping;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.RETURN;

class RemapperTest {
    private static MappingData mappings;

    @BeforeAll
    static void load() throws IOException {
        mappings = MappingTestData.load();
    }

    // ---- 原版 jar：官方名 → SRG ----

    @Test
    void gameRemapFollowsTheClassHierarchy(@TempDir Path dir) throws IOException {
        Map<String, byte[]> jar = new LinkedHashMap<>();
        jar.put("a.class", cls("a", "java/lang/Object", 0, cw -> {
            cw.visitField(ACC_PUBLIC, "b", "I", null, null).visitEnd();
            empty(cw, ACC_PUBLIC, "a", "()V");
            empty(cw, ACC_PRIVATE, "c", "()V");
        }));
        jar.put("a$a.class", cls("a$a", "java/lang/Object", 0, cw -> {
        }));
        jar.put("b.class", cls("b", "a", 0, cw -> {
            empty(cw, ACC_PUBLIC, "a", "()V");
            empty(cw, ACC_PUBLIC, "c", "()V");
        }));
        jar.put("c.class", cls("c", "java/lang/Object", ACC_INTERFACE | ACC_ABSTRACT, cw ->
                cw.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, "a", "(I)I", null, null).visitEnd()));
        // d 不在映射表里：它对父类成员的引用要沿继承链找到 a 上的映射
        jar.put("d.class", cls("d", "a", 0, cw -> code(cw, "go", mv -> {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, "d", "a", "()V", false);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, "d", "b", "I");
            mv.visitInsn(POP);
            // 自己声明的私有方法 c 不能借用父类私有方法 c 的映射
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, "d", "c", "()V", false);
            Handle bsm = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                            + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                            + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
            mv.visitInvokeDynamicInsn("a", "()Lc;", bsm, Type.getType("(I)I"),
                    new Handle(Opcodes.H_INVOKESTATIC, "d", "lambda$0", "(I)I", false), Type.getType("(I)I"));
            mv.visitInsn(POP);
        }, cw2 -> empty(cw2, ACC_PRIVATE, "c", "()V"))));
        jar.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
        jar.put("META-INF/MOJANGCS.SF", new byte[]{1});
        jar.put("version.json", "{}".getBytes(StandardCharsets.UTF_8));
        Path in = dir.resolve("in.jar");
        JarIO.write(in, jar);
        Path out = dir.resolve("out.jar");

        int n = new GameJarRemapper(mappings, Mappings.OFFICIAL, Mappings.SRG).remap(in, out);
        assertEquals(5, n);

        Map<String, byte[]> result = JarIO.read(out).stream()
                .collect(Collectors.toMap(JarIO.Entry::name, JarIO.Entry::data, (x, y) -> x, LinkedHashMap::new));
        assertTrue(result.containsKey("net/minecraft/world/Thing.class"), result.keySet().toString());
        assertTrue(result.containsKey("net/minecraft/world/Thing$Inner.class"));
        assertTrue(result.containsKey("d.class"));
        assertTrue(result.containsKey("version.json"));
        assertTrue(!result.containsKey("META-INF/MOJANGCS.SF"), "signature files are dropped");

        ClassNode thing = node(result.get("net/minecraft/world/Thing.class"));
        assertEquals("Thing.java", thing.sourceFile);
        assertTrue(thing.methods.stream().anyMatch(m -> m.name.equals("m_100_")));
        assertTrue(thing.methods.stream().anyMatch(m -> m.name.equals("m_101_")));
        assertEquals("f_200_", thing.fields.get(0).name);
        assertEquals("Thing.java", node(result.get("net/minecraft/world/Thing$Inner.class")).sourceFile);

        ClassNode sub = node(result.get("net/minecraft/world/SubThing.class"));
        assertEquals("net/minecraft/world/Thing", sub.superName);
        assertTrue(sub.methods.stream().anyMatch(m -> m.name.equals("m_100_")));
        assertTrue(sub.methods.stream().anyMatch(m -> m.name.equals("m_102_")));

        ClassNode d = node(result.get("d.class"));
        assertEquals("net/minecraft/world/Thing", d.superName);
        List<AbstractInsnNode> insns = insns(method(d, "go"));
        MethodInsnNode call = (MethodInsnNode) insns.stream().filter(i -> i instanceof MethodInsnNode).findFirst()
                .orElseThrow();
        assertEquals("m_100_", call.name);
        FieldInsnNode get = (FieldInsnNode) insns.stream().filter(i -> i instanceof FieldInsnNode).findFirst()
                .orElseThrow();
        assertEquals("f_200_", get.name);
        MethodInsnNode privateCall = (MethodInsnNode) insns.stream().filter(i -> i instanceof MethodInsnNode)
                .skip(1).findFirst().orElseThrow();
        assertEquals("c", privateCall.name);
        InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insns.stream()
                .filter(i -> i instanceof InvokeDynamicInsnNode).findFirst().orElseThrow();
        assertEquals("m_103_", indy.name);
        assertEquals("()Lnet/minecraft/world/Ticker;", indy.desc);
        assertNotNull(method(d, "c"));
    }

    // ---- 模组 jar：intermediary → SRG ----

    @Test
    void modRemapUsesUniqueNamesStringsAndRefmaps(@TempDir Path dir) throws IOException {
        Map<String, byte[]> jar = new LinkedHashMap<>();
        jar.put("com/example/ModThing.class", cls("com/example/ModThing", "net/minecraft/class_1", 0, cw -> {
            AnnotationVisitor mixin = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
            AnnotationVisitor targets = mixin.visitArray("targets");
            targets.visit(null, "net.minecraft.class_2");
            targets.visitEnd();
            mixin.visitEnd();
            empty(cw, ACC_PUBLIC, "method_10", "()V");
            code(cw, "go", mv -> {
                AnnotationVisitor at = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/At;", true);
                at.visit("target", "Lnet/minecraft/class_1;method_10()V");
                at.visitEnd();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/class_2", "method_12", "()V", false);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, "net/minecraft/class_1", "field_20", "I");
                mv.visitInsn(POP);
                mv.visitLdcInsn("net.minecraft.class_3");
                mv.visitInsn(POP);
                mv.visitLdcInsn("not a name: methodical field_trip");
                mv.visitInsn(POP);
            }, cw2 -> {
            });
        }));
        jar.put("mod.refmap.json", """
                {
                  "mappings": {
                    "com/example/ModMixin": {
                      "tick": "Lnet/minecraft/class_1;method_10()V",
                      "count": "Lnet/minecraft/class_1;field_20:I",
                      "Lnet/minecraft/class_1;<init>()V": "Lnet/minecraft/class_1;<init>()V"
                    }
                  },
                  "data": {"named:intermediary": {"com/example/ModMixin": {"apply": "method_13"}}}
                }""".getBytes(StandardCharsets.UTF_8));
        jar.put("data/example/recipe.json", "{\"type\": \"class_1\"}".getBytes(StandardCharsets.UTF_8));
        Path in = dir.resolve("mod.jar");
        JarIO.write(in, jar);
        Path out = dir.resolve("mod-srg.jar");

        new ModJarRemapper(mappings, Mappings.INTERMEDIARY, Mappings.SRG).remap(in, out);
        Map<String, byte[]> result = JarIO.read(out).stream()
                .collect(Collectors.toMap(JarIO.Entry::name, JarIO.Entry::data));

        ClassNode mod = node(result.get("com/example/ModThing.class"));
        assertEquals("net/minecraft/world/Thing", mod.superName);
        assertNotNull(method(mod, "m_100_"), "override of a game method is renamed");
        AnnotationNode mixin = mod.invisibleAnnotations.get(0);
        assertEquals(List.of("net.minecraft.world.SubThing"), mixin.values.get(1));
        MethodNode go = method(mod, "go");
        assertEquals("Lnet/minecraft/world/Thing;m_100_()V", go.visibleAnnotations.get(0).values.get(1));
        List<AbstractInsnNode> insns = insns(go);
        MethodInsnNode call = (MethodInsnNode) insns.stream().filter(i -> i instanceof MethodInsnNode).findFirst()
                .orElseThrow();
        assertEquals("net/minecraft/world/SubThing", call.owner);
        assertEquals("m_102_", call.name);
        FieldInsnNode get = (FieldInsnNode) insns.stream().filter(i -> i instanceof FieldInsnNode).findFirst()
                .orElseThrow();
        assertEquals("f_200_", get.name);
        List<Object> constants = insns.stream().filter(i -> i instanceof LdcInsnNode)
                .map(i -> ((LdcInsnNode) i).cst).toList();
        assertEquals(List.of("net.minecraft.world.Ticker", "not a name: methodical field_trip"), constants);

        String refmap = new String(result.get("mod.refmap.json"), StandardCharsets.UTF_8);
        assertTrue(refmap.contains("\"tick\": \"Lnet/minecraft/world/Thing;m_100_()V\""), refmap);
        assertTrue(refmap.contains("\"count\": \"Lnet/minecraft/world/Thing;f_200_:I\""), refmap);
        assertTrue(refmap.contains("\"apply\": \"m_103_\""), refmap);
        assertEquals("{\"type\": \"class_1\"}", new String(result.get("data/example/recipe.json"),
                StandardCharsets.UTF_8), "ordinary json is left alone");
    }

    // ---- 生成测试用的类 ----

    private interface Body {
        void accept(ClassWriter cw);
    }

    private interface Code {
        void accept(MethodVisitor mv);
    }

    private static byte[] cls(String name, String superName, int extraAccess, Body body) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, ACC_PUBLIC | extraAccess, name, null, superName, null);
        cw.visitSource("SourceFile", null);
        if ((extraAccess & ACC_INTERFACE) == 0) {
            MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            init.visitCode();
            init.visitVarInsn(ALOAD, 0);
            init.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false);
            init.visitInsn(RETURN);
            init.visitMaxs(0, 0);
            init.visitEnd();
        }
        body.accept(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void empty(ClassWriter cw, int access, String name, String desc) {
        MethodVisitor mv = cw.visitMethod(access, name, desc, null, null);
        mv.visitCode();
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void code(ClassWriter cw, String name, Code code, Body more) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, name, "()V", null, null);
        mv.visitCode();
        code.accept(mv);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        more.accept(cw);
    }

    private static ClassNode node(byte[] bytes) {
        assertNotNull(bytes);
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, 0);
        return n;
    }

    private static MethodNode method(ClassNode n, String name) {
        return n.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElse(null);
    }

    private static List<AbstractInsnNode> insns(MethodNode m) {
        List<AbstractInsnNode> list = new ArrayList<>();
        m.instructions.forEach(list::add);
        return list;
    }
}
