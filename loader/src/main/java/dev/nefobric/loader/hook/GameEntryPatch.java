package dev.nefobric.loader.hook;

import dev.nefobric.loader.Env;
import dev.nefobric.loader.launch.GameLocator;
import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import dev.nefobric.loader.classloading.ClassTransformer;

/**
 * 游戏入口钩子：在原版主类里插入一次 {@link GameHooks} 调用，作为所有模组初始化的起点。
 *
 * <p>两个主类 {@code net.minecraft.client.main.Main} 和 {@code net.minecraft.server.Main} 在原版里不混淆，
 * 所以这里不需要任何映射表，只靠字符串常量定位：
 * <ul>
 *   <li>客户端：{@code Thread.currentThread().setName("Render thread")} 之后、构造游戏对象之前。
 *       此时 Bootstrap 已完成，代码运行在渲染线程上；</li>
 *   <li>专用服务端：读取 {@code "server.properties"} 之前（Bootstrap 之后），与 Fabric Loader 选择的位置相同，
 *       保证模组在服务器设置和世界加载之前初始化。</li>
 * </ul>
 * 找不到预期位置时不修改类，由启动器退回到“游戏主类运行前初始化”并给出警告。
 */
public final class GameEntryPatch implements ClassTransformer {
    static final String HOOKS = "dev/nefobric/loader/hook/GameHooks";
    static final String CLIENT_ANCHOR = "Render thread";
    static final String SERVER_ANCHOR = "server.properties";

    private final Env env;
    private final String targetClass;
    private volatile boolean applied;

    public GameEntryPatch(Env env) {
        this.env = env;
        this.targetClass = env == Env.CLIENT ? GameLocator.CLIENT_MAIN : GameLocator.SERVER_MAIN;
    }

    public String targetClass() {
        return targetClass;
    }

    public boolean isApplied() {
        return applied;
    }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        if (!className.equals(targetClass)) {
            return bytes;
        }
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            MethodNode main = null;
            for (MethodNode m : node.methods) {
                if (m.name.equals("main") && m.desc.equals("([Ljava/lang/String;)V")
                        && (m.access & Opcodes.ACC_STATIC) != 0) {
                    main = m;
                }
            }
            boolean ok = main != null && (env == Env.CLIENT ? patchClient(main) : patchServer(main));
            if (!ok) {
                Log.warn(Messages.tr("hook.notFound", targetClass,
                        env == Env.CLIENT ? CLIENT_ANCHOR : SERVER_ANCHOR));
                return bytes;
            }
            // 只插入一条不影响操作数栈的调用，原有的栈帧仍然有效，不需要重新计算
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(cw);
            applied = true;
            Log.info(Messages.tr("hook.applied", targetClass));
            return cw.toByteArray();
        } catch (RuntimeException e) {
            Log.error(Messages.tr("hook.failed", targetClass), e);
            return bytes;
        }
    }

    static boolean patchClient(MethodNode main) {
        boolean sawAnchor = false;
        for (AbstractInsnNode insn = main.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode ldc && CLIENT_ANCHOR.equals(ldc.cst)) {
                sawAnchor = true;
            } else if (sawAnchor && insn instanceof MethodInsnNode call
                    && call.owner.equals("java/lang/Thread") && call.name.equals("setName")) {
                main.instructions.insert(call, hookCall("onClientStart"));
                return true;
            }
        }
        return false;
    }

    static boolean patchServer(MethodNode main) {
        for (AbstractInsnNode insn = main.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode ldc && SERVER_ANCHOR.equals(ldc.cst)) {
                main.instructions.insertBefore(ldc, hookCall("onServerStart"));
                return true;
            }
        }
        return false;
    }

    private static MethodInsnNode hookCall(String name) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, Type.getMethodDescriptor(Type.VOID_TYPE), false);
    }
}
