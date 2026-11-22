package dev.nefobric.loader.hook;

import dev.nefobric.loader.Env;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEntryPatchTest {

    private static byte[] mainClass(String internalName, Consumer<MethodVisitor> body) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        mv.visitCode();
        body.accept(mv);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static MethodNode main(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node.methods.stream().filter(m -> m.name.equals("main")).findFirst().orElseThrow();
    }

    private static AbstractInsnNode findHook(MethodNode m, String name) {
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof MethodInsnNode call && call.owner.equals(GameEntryPatch.HOOKS) && call.name.equals(name)) {
                return i;
            }
        }
        return null;
    }

    @Test
    void clientHookGoesRightAfterRenderThreadRename() {
        byte[] original = mainClass("net/minecraft/client/main/Main", mv -> {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
            mv.visitLdcInsn("Render thread");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Thread", "setName", "(Ljava/lang/String;)V", false);
        });
        GameEntryPatch patch = new GameEntryPatch(Env.CLIENT, n -> null);
        byte[] patched = patch.transform("net.minecraft.client.main.Main", original);
        assertTrue(patch.isApplied());
        AbstractInsnNode hook = findHook(main(patched), "onClientStart");
        assertNotNull(hook);
        MethodInsnNode before = (MethodInsnNode) hook.getPrevious();
        assertTrue(before.name.equals("setName"));
    }

    @Test
    void serverHookGoesRightBeforeServerProperties() {
        byte[] original = mainClass("net/minecraft/server/Main", mv -> {
            mv.visitLdcInsn("server.properties");
            mv.visitInsn(Opcodes.POP);
        });
        GameEntryPatch patch = new GameEntryPatch(Env.SERVER, n -> null);
        byte[] patched = patch.transform("net.minecraft.server.Main", original);
        assertTrue(patch.isApplied());
        AbstractInsnNode hook = findHook(main(patched), "onServerStart");
        assertNotNull(hook);
        assertTrue(hook.getNext() instanceof LdcInsnNode ldc && "server.properties".equals(ldc.cst));
    }

    @Test
    void unknownCodeIsLeftUntouched() {
        byte[] original = mainClass("net/minecraft/client/main/Main", mv -> {
            mv.visitLdcInsn("something else");
            mv.visitInsn(Opcodes.POP);
        });
        GameEntryPatch patch = new GameEntryPatch(Env.CLIENT, n -> null);
        assertArrayEquals(original, patch.transform("net.minecraft.client.main.Main", original));
        assertFalse(patch.isApplied());
    }

    @Test
    void otherClassesAreIgnored() {
        byte[] bytes = new byte[]{1, 2, 3};
        assertSame(bytes, new GameEntryPatch(Env.CLIENT, n -> null).transform("net.minecraft.server.Main", bytes));
    }

    /** 和原版 Minecraft 一样：super()，登记实例，再给 File 字段 gameDirectory 赋值。 */
    private static byte[] gameClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "net/minecraft/client/Game", null, "java/lang/Object", null);
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "gameDirectory", "Ljava/io/File;", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "assetsDirectory", "Ljava/io/File;", null, null).visitEnd();
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        for (String field : new String[]{"gameDirectory", "assetsDirectory"}) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File");
            mv.visitInsn(Opcodes.DUP);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false);
            mv.visitFieldInsn(Opcodes.PUTFIELD, "net/minecraft/client/Game", field, "Ljava/io/File;");
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] clientMainCreatingGame() {
        return mainClass("net/minecraft/client/main/Main", mv -> {
            mv.visitTypeInsn(Opcodes.NEW, "net/minecraft/client/main/GameConfig");
            mv.visitInsn(Opcodes.POP);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
            mv.visitLdcInsn("Render thread");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Thread", "setName", "(Ljava/lang/String;)V", false);
            mv.visitTypeInsn(Opcodes.NEW, "net/minecraft/client/Game");
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn(".");
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/client/Game", "<init>", "(Ljava/lang/String;)V", false);
            mv.visitInsn(Opcodes.POP);
        });
    }

    @Test
    void clientHookGoesIntoGameConstructorAfterGameDirectory() {
        byte[] game = gameClass();
        GameEntryPatch patch = new GameEntryPatch(Env.CLIENT, n -> n.equals("net.minecraft.client.Game") ? game : null);
        byte[] main = clientMainCreatingGame();
        // 主类不动，钩子位置已经确定
        assertSame(main, patch.transform("net.minecraft.client.main.Main", main));
        assertTrue(patch.isApplied());

        byte[] patched = patch.transform("net.minecraft.client.Game", game);
        ClassNode node = new ClassNode();
        new ClassReader(patched).accept(node, 0);
        MethodNode ctor = node.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        AbstractInsnNode hook = findHook(ctor, "onClientGameCreated");
        assertNotNull(hook);
        assertEquals("(Ljava/lang/Object;)V", ((MethodInsnNode) hook).desc);
        assertTrue(hook.getPrevious() instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD && v.var == 0);
        FieldInsnNode store = (FieldInsnNode) hook.getPrevious().getPrevious();
        assertEquals("gameDirectory", store.name, "right after the first File field, not a later one");

        // 改过的类仍能通过校验
        StringWriter errors = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(patched), false, new PrintWriter(errors));
        assertEquals("", errors.toString());
    }

    @Test
    void clientFallsBackToMainWhenGameConstructorHasNoFileField() {
        byte[] main = clientMainCreatingGame();
        byte[] emptyGame = mainClass("net/minecraft/client/Game", mv -> {
        });
        GameEntryPatch patch = new GameEntryPatch(Env.CLIENT, n -> emptyGame);
        byte[] patched = patch.transform("net.minecraft.client.main.Main", main);
        assertTrue(patch.isApplied());
        assertNotNull(findHook(main(patched), "onClientStart"));
        assertSame(emptyGame, patch.transform("net.minecraft.client.Game", emptyGame));
    }
}
