package dev.nefobric.compat.forge.devtools.remap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

/** 用一个三个类的“混淆游戏”验证 named 和 reobf 两步，包括继承来的成员、覆盖的方法和 lambda。 */
class McRemapTest {
    private static final String PROGUARD = """
            # comment
            net.minecraft.world.Base -> a:
                int count -> a
                int getCount() -> a
                void copyFrom(net.minecraft.world.Base) -> b
            net.minecraft.world.Child -> b:
                1:2:int getCount() -> a
            net.minecraft.world.Factory -> c:
                net.minecraft.world.Child create(net.minecraft.world.Base) -> a
            """;
    private static final String TSRG = """
            tsrg2 obf srg id
            a net/minecraft/src/C_1_ 1
            \ta f_100_ 100
            \ta ()I m_200_ 200
            \tb (La;)V m_201_ 201
            \t\t0 o p_1_ 1
            b net/minecraft/src/C_2_ 2
            \ta ()I m_200_ 200
            c net/minecraft/src/C_3_ 3
            \ta (La;)Lb; m_300_ 300
            """;

    @Test
    void namedThenReobf(@TempDir Path dir) throws IOException {
        Path client = dir.resolve("client.txt");
        Files.writeString(client, PROGUARD);
        Path tsrg = dir.resolve("joined.tsrg");
        Files.writeString(tsrg, TSRG);

        Path obf = jar(dir.resolve("obf.jar"), Map.of("a", obfBase(), "b", obfChild(), "c", obfFactory(),
                "META-INF/MOJANGCS.SF", new byte[]{1}));
        Path named = dir.resolve("named.jar");
        assertEquals(3, McRemap.named(obf, client, named));
        Map<String, ClassNode> n = read(named);
        assertEquals(Set.of("net/minecraft/world/Base", "net/minecraft/world/Child", "net/minecraft/world/Factory"),
                n.keySet());
        try (ZipFile z = new ZipFile(named.toFile())) {
            // 目录条目按改名后的包重建，签名文件去掉
            assertTrue(z.getEntry("net/minecraft/world/").isDirectory());
            assertEquals(null, z.getEntry("META-INF/MOJANGCS.SF"));
        }
        ClassNode base = n.get("net/minecraft/world/Base");
        assertEquals("count", base.fields.get(0).name);
        assertEquals(Set.of("<init>", "getCount", "copyFrom"), names(base));
        assertEquals("(Lnet/minecraft/world/Base;)V", method(base, "copyFrom").desc);
        ClassNode child = n.get("net/minecraft/world/Child");
        assertEquals("net/minecraft/world/Base", child.superName);
        // 子类里通过子类引用父类字段：沿继承关系找到 Base.count
        FieldInsnNode get = first(method(child, "c"), FieldInsnNode.class);
        assertEquals("net/minecraft/world/Child", get.owner);
        assertEquals("count", get.name);

        Path mine = jar(dir.resolve("mine.jar"), Map.of("user/My", myClass()));
        Path out = dir.resolve("mine-srg.jar");
        assertEquals(1, McRemap.reobf(mine, out, client, tsrg, named, List.of()));
        ClassNode my = read(out).get("user/My");
        // 覆盖原版方法：改成 SRG 名
        assertTrue(names(my).contains("m_200_"), names(my).toString());
        MethodNode use = method(my, "use");
        assertEquals("f_100_", first(use, FieldInsnNode.class).name);
        MethodInsnNode call = first(use, MethodInsnNode.class);
        assertEquals("net/minecraft/world/Child", call.owner);
        assertEquals("m_200_", call.name);
        // lambda 实现原版的函数式接口：接口方法名也改
        assertEquals("m_300_", first(use, InvokeDynamicInsnNode.class).name);
        // 自己的方法不动
        assertTrue(names(my).contains("use"));
    }

    @Test
    void readsMcpConfigZip(@TempDir Path dir) throws IOException {
        Path zip = jar(dir.resolve("mcp_config.zip"), Map.of("config/joined.tsrg", TSRG.getBytes()));
        TsrgMappings t = McRemap.readTsrg(zip);
        assertEquals("f_100_", t.field("a", "a"));
        assertEquals("m_300_", t.method("c", "a", "(La;)Lb;"));
    }

    // —— 生成测试用的类 ——

    private static byte[] obfBase() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, ACC_PUBLIC, "a", null, "java/lang/Object", null);
        cw.visitField(ACC_PUBLIC, "a", "I", null, null).visitEnd();
        ctor(cw, "java/lang/Object");
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "a", "()I", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, "a", "a", "I");
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_PUBLIC, "b", "(La;)V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] obfChild() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, ACC_PUBLIC, "b", null, "a", null);
        ctor(cw, "a");
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "a", "()I", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_PUBLIC, "c", "()I", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, "b", "a", "I");
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] obfFactory() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT, "c", null, "java/lang/Object", null);
        cw.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, "a", "(La;)Lb;", null, null).visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** 按 Mojang 名写的“自己的代码”：继承 Child、覆盖 getCount、读 count、用 lambda 实现 Factory。 */
    private static byte[] myClass() {
        String child = "net/minecraft/world/Child";
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, ACC_PUBLIC, "user/My", null, child, null);
        ctor(cw, child);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "getCount", "()I", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.ICONST_2);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_PUBLIC, "use", "(L" + child + ";)I", null, null);
        mv.visitCode();
        mv.visitInvokeDynamicInsn("create", "()Lnet/minecraft/world/Factory;",
                new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                                + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
                Type.getType("(Lnet/minecraft/world/Base;)Lnet/minecraft/world/Child;"),
                new Handle(Opcodes.H_INVOKESTATIC, "user/My", "lambda$use$0",
                        "(Lnet/minecraft/world/Base;)Lnet/minecraft/world/Child;", false),
                Type.getType("(Lnet/minecraft/world/Base;)Lnet/minecraft/world/Child;"));
        mv.visitInsn(Opcodes.POP);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.GETFIELD, child, "count", "I");
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, child, "getCount", "()I", false);
        mv.visitInsn(Opcodes.IADD);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void ctor(ClassWriter cw, String superName) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    // —— 工具 ——

    private static Path jar(Path path, Map<String, byte[]> entries) throws IOException {
        try (OutputStream os = Files.newOutputStream(path); ZipOutputStream zout = new ZipOutputStream(os)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                String name = e.getKey().contains("/") && !e.getKey().startsWith("user/") ? e.getKey()
                        : e.getKey() + ".class";
                zout.putNextEntry(new ZipEntry(name));
                zout.write(e.getValue());
                zout.closeEntry();
            }
        }
        return path;
    }

    private static Map<String, ClassNode> read(Path jar) throws IOException {
        Map<String, ClassNode> out = new HashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry e : java.util.Collections.list(zip.entries())) {
                if (e.getName().endsWith(".class")) {
                    ClassNode cn = new ClassNode();
                    new ClassReader(zip.getInputStream(e).readAllBytes()).accept(cn, 0);
                    out.put(cn.name, cn);
                } else {
                    assertTrue(!e.getName().endsWith(".SF"), "signature files are dropped");
                }
            }
        }
        return out;
    }

    private static Set<String> names(ClassNode cn) {
        return cn.methods.stream().map(m -> m.name).collect(Collectors.toSet());
    }

    private static MethodNode method(ClassNode cn, String name) {
        return cn.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static <T extends AbstractInsnNode> T first(MethodNode m, Class<T> type) {
        for (AbstractInsnNode insn : m.instructions) {
            if (type.isInstance(insn)) {
                return type.cast(insn);
            }
        }
        throw new AssertionError("no " + type.getSimpleName() + " in " + m.name);
    }
}
