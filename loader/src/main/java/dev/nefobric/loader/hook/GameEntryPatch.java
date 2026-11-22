package dev.nefobric.loader.hook;

import dev.nefobric.loader.Env;
import dev.nefobric.loader.classloading.ClassTransformer;
import dev.nefobric.loader.launch.GameLocator;
import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;

/**
 * 游戏入口钩子：在原版里插入一次 {@link GameHooks} 调用，作为所有模组初始化的起点。不需要映射表，只靠字符串常量
 * 和字节码结构定位，所以混淆、重映射前后都能用。
 *
 * <ul>
 *   <li>客户端：和 Fabric Loader 相同，插在游戏对象（{@code Minecraft}）的构造函数里、第一次给 {@code File} 类型的字段
 *       （{@code gameDirectory}）赋值之后。游戏对象是主类里 {@code setName("Render thread")} 之后第一个 {@code new}
 *       出来的 {@code net.minecraft} 类。这时渲染线程已经初始化，{@code Minecraft.getInstance()} 和游戏目录都已就绪，
 *       模组初始化失败时原版能正常写出 “Initializing game” 崩溃报告；钩子还会把游戏对象交给
 *       {@code NeFobricLoader#gameInstance()}。找不到这个位置时退回到主类里 {@code setName} 之后。</li>
 *   <li>专用服务端：主类里读取 {@code "server.properties"} 之前（Bootstrap 之后），与 Fabric Loader 选择的位置相同，
 *       保证模组在服务器设置和世界加载之前初始化。</li>
 * </ul>
 * 两处都找不到时不修改类，由启动器退回到“游戏主类运行前初始化”并给出警告。
 */
public final class GameEntryPatch implements ClassTransformer {
    static final String HOOKS = "dev/nefobric/loader/hook/GameHooks";
    static final String CLIENT_ANCHOR = "Render thread";
    static final String SERVER_ANCHOR = "server.properties";
    static final String FILE_DESC = "Ljava/io/File;";

    /** 读另一个类的原始字节码，用来在改主类时先确认游戏对象的构造函数里有插入点。 */
    @FunctionalInterface
    public interface ClassBytes {
        byte[] get(String className) throws IOException;
    }

    private final Env env;
    private final String targetClass;
    private final ClassBytes classBytes;
    /** 客户端游戏对象的类名（点号分隔）；钩子插在它的构造函数里。主类改完之后才知道。 */
    private volatile String gameClass;
    private volatile boolean applied;

    public GameEntryPatch(Env env, ClassBytes classBytes) {
        this.env = env;
        this.targetClass = env == Env.CLIENT ? GameLocator.CLIENT_MAIN : GameLocator.SERVER_MAIN;
        this.classBytes = classBytes;
    }

    public String targetClass() {
        return targetClass;
    }

    /** 主类已经处理过，并且钩子位置已经确定（在主类里，或者在游戏对象的构造函数里）。 */
    public boolean isApplied() {
        return applied;
    }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        if (className.equals(targetClass)) {
            return patchMain(bytes);
        }
        String game = gameClass;
        if (game != null && className.equals(game)) {
            return patchGameConstructor(className, bytes);
        }
        return bytes;
    }

    private byte[] patchMain(byte[] bytes) {
        try {
            ClassNode node = read(bytes);
            MethodNode main = findMain(node);
            boolean ok;
            if (main == null) {
                ok = false;
            } else if (env == Env.CLIENT) {
                String game = findClientGameClass(main);
                if (game != null && hasHookPoint(game)) {
                    gameClass = game.replace('/', '.');
                    applied = true;
                    Log.debug("Client hook goes into the constructor of " + gameClass);
                    return bytes;
                }
                Log.debug("No constructor hook point found (game class " + game + "); hooking " + targetClass);
                ok = patchClient(main);
            } else {
                ok = patchServer(main);
            }
            if (!ok) {
                Log.warn(Messages.tr("hook.notFound", targetClass,
                        env == Env.CLIENT ? CLIENT_ANCHOR : SERVER_ANCHOR));
                return bytes;
            }
            applied = true;
            Log.info(Messages.tr("hook.applied", targetClass));
            return write(node);
        } catch (RuntimeException e) {
            Log.error(Messages.tr("hook.failed", targetClass), e);
            return bytes;
        }
    }

    private byte[] patchGameConstructor(String className, byte[] bytes) {
        ClassNode node = read(bytes);
        String internal = className.replace('.', '/');
        int patched = 0;
        for (MethodNode m : node.methods) {
            if (m.name.equals("<init>") && insertAfterGameDirectory(m, internal)) {
                patched++;
            }
        }
        if (patched == 0) {
            // 改主类时已经确认过有插入点；走到这里说明字节码在两次读取之间变了
            throw new IllegalStateException("NeFobric game hook: no File field assignment in " + className
                    + " constructors any more");
        }
        Log.info(Messages.tr("hook.applied", className));
        return write(node);
    }

    /** 主类里 “Render thread” 之后第一个 new 出来的 net.minecraft 类（主类自己的包除外），内部名。 */
    static String findClientGameClass(MethodNode main) {
        boolean sawAnchor = false;
        for (AbstractInsnNode insn = main.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode ldc && CLIENT_ANCHOR.equals(ldc.cst)) {
                sawAnchor = true;
            } else if (sawAnchor && insn.getOpcode() == Opcodes.NEW && insn instanceof TypeInsnNode type
                    && type.desc.startsWith("net/minecraft/") && !type.desc.startsWith("net/minecraft/client/main/")) {
                return type.desc;
            }
        }
        return null;
    }

    private boolean hasHookPoint(String internalName) {
        byte[] game;
        try {
            game = classBytes.get(internalName.replace('/', '.'));
        } catch (IOException e) {
            Log.debug("Cannot read " + internalName + ": " + e);
            return false;
        }
        if (game == null) {
            return false;
        }
        ClassNode node = read(game);
        for (MethodNode m : node.methods) {
            if (m.name.equals("<init>") && findGameDirectoryStore(m, internalName) != null) {
                return true;
            }
        }
        return false;
    }

    /** 构造函数里第一次给本类 File 字段赋值的 PUTFIELD。 */
    static FieldInsnNode findGameDirectoryStore(MethodNode ctor, String owner) {
        for (AbstractInsnNode insn = ctor.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.PUTFIELD && insn instanceof FieldInsnNode f
                    && f.owner.equals(owner) && f.desc.equals(FILE_DESC)) {
                return f;
            }
        }
        return null;
    }

    static boolean insertAfterGameDirectory(MethodNode ctor, String owner) {
        FieldInsnNode store = findGameDirectoryStore(ctor, owner);
        if (store == null) {
            return false;
        }
        // this 在这里已经初始化（字段赋值在 super() 之后）；插入的代码对操作数栈的净影响为零，原有栈帧仍然有效
        InsnList call = new InsnList();
        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "onClientGameCreated",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Object.class)), false));
        ctor.instructions.insert(store, call);
        return true;
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

    private static MethodNode findMain(ClassNode node) {
        for (MethodNode m : node.methods) {
            if (m.name.equals("main") && m.desc.equals("([Ljava/lang/String;)V")
                    && (m.access & Opcodes.ACC_STATIC) != 0) {
                return m;
            }
        }
        return null;
    }

    private static MethodInsnNode hookCall(String name) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, Type.getMethodDescriptor(Type.VOID_TYPE), false);
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    /** 只插入不影响操作数栈净值的调用，原有的栈帧仍然有效，不需要重新计算。 */
    private static byte[] write(ClassNode node) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(cw);
        return cw.toByteArray();
    }
}
