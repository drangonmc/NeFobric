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

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        GameEntryPatch patch = new GameEntryPatch(Env.CLIENT);
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
        GameEntryPatch patch = new GameEntryPatch(Env.SERVER);
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
        GameEntryPatch patch = new GameEntryPatch(Env.CLIENT);
        assertArrayEquals(original, patch.transform("net.minecraft.client.main.Main", original));
        assertFalse(patch.isApplied());
    }

    @Test
    void otherClassesAreIgnored() {
        byte[] bytes = new byte[]{1, 2, 3};
        assertSame(bytes, new GameEntryPatch(Env.CLIENT).transform("net.minecraft.server.Main", bytes));
    }
}
