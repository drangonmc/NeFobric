package dev.nefobric.compat.forge.transform;

import dev.nefobric.loader.classloading.ClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * ACCESS 阶段（在 AT 之后）：让实现了 {@code net.minecraftforge.common.IExtensibleEnum} 的枚举能在运行时追加常量。
 *
 * <p>枚举里每个 {@code static create(String name, A a, B b...)} 的方法体换成：</p>
 * <pre>
 * synchronized static E create(String name, A a, B b...) {
 *     Object old = ExtensibleEnums.existing(name, $VALUES);   // 同名（不分大小写）已有就返回它
 *     if (old != null) return (E) old;
 *     E e = new E(name, $VALUES.length, a, b...);              // 对应的构造器 (String, int, A, B...)
 *     $VALUES = (E[]) ExtensibleEnums.append($VALUES, e);
 *     ExtensibleEnums.reset(E.class);                          // 让 valueOf、EnumSet 看到新常量
 *     e.init();
 *     return e;
 * }
 * </pre>
 * <p>{@code $VALUES} 去掉 final。{@code create} 的参数不对（第一个不是 String、返回类型不是这个枚举、没有对应的
 * 构造器）时和 Forge 一样直接报错，类加载失败。原版枚举的 {@code create} 方法由 {@link ExtensibleVanillaEnums}
 * 在 GAME_PATCH 阶段加上。</p>
 */
public final class EnumExtender implements ClassTransformer {
    static final String MARKER = "net/minecraftforge/common/IExtensibleEnum";
    private static final byte[] MARKER_BYTES = MARKER.getBytes(StandardCharsets.UTF_8);
    /** 运行时的小工具，见 forge-runtime 里的 dev.nefobric.forge.enums.ExtensibleEnums。 */
    static final String HELPER = "dev/nefobric/forge/enums/ExtensibleEnums";
    private static final String EXISTING_DESC = "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String APPEND_DESC = "([Ljava/lang/Object;Ljava/lang/Object;)[Ljava/lang/Object;";
    private static final String RESET_DESC = "(Ljava/lang/Class;)V";
    private static final Type STRING = Type.getType(String.class);

    @Override
    public byte[] transform(String className, byte[] bytes) {
        if (!OnlyInStripper.contains(bytes, MARKER_BYTES)) {
            return bytes;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (!extend(node)) {
            return bytes;
        }
        // 只改了 create 的方法体，栈帧自己写了，不需要 COMPUTE_FRAMES（那要加载类层次）
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    /** @return 是否有改动 */
    static boolean extend(ClassNode node) {
        if ((node.access & Opcodes.ACC_ENUM) == 0 || !node.interfaces.contains(MARKER)) {
            return false;
        }
        Type self = Type.getObjectType(node.name);
        List<MethodNode> factories = new ArrayList<>();
        for (MethodNode m : node.methods) {
            if ((m.access & Opcodes.ACC_STATIC) != 0 && m.name.equals("create")) {
                factories.add(m);
            }
        }
        if (factories.isEmpty()) {
            throw new IllegalStateException("IExtensibleEnum " + self.getClassName() + " has no create method");
        }
        if ((node.access & Opcodes.ACC_ABSTRACT) != 0) {
            throw new IllegalStateException("IExtensibleEnum " + self.getClassName()
                    + " is abstract (constants with bodies), it cannot be extended");
        }
        FieldNode values = valuesField(node, self);
        for (MethodNode factory : factories) {
            Type[] args = Type.getArgumentTypes(factory.desc);
            if (args.length == 0 || !args[0].equals(STRING)) {
                throw new IllegalStateException("Enum " + self.getClassName()
                        + " has a create method without String as first parameter: create" + factory.desc);
            }
            if (!Type.getReturnType(factory.desc).equals(self)) {
                throw new IllegalStateException("Enum " + self.getClassName()
                        + " has a create method with the wrong return type: create" + factory.desc);
            }
            String ctorDesc = constructorDesc(args);
            boolean hasCtor = node.methods.stream().anyMatch(m -> m.name.equals("<init>") && m.desc.equals(ctorDesc));
            if (!hasCtor) {
                throw new IllegalStateException("Enum " + self.getClassName() + " has create" + factory.desc
                        + " but no constructor " + ctorDesc);
            }
            if (values == null) {
                throw new IllegalStateException("Enum " + self.getClassName()
                        + " has create methods but no synthetic $VALUES array");
            }
            rewrite(node, factory, args, ctorDesc, values);
        }
        values.access &= ~Opcodes.ACC_FINAL;
        return true;
    }

    /** 编译器生成的常量数组：静态、合成、类型是本枚举的数组；有多个时取叫 $VALUES 的。 */
    private static FieldNode valuesField(ClassNode node, Type self) {
        String desc = "[" + self.getDescriptor();
        FieldNode found = null;
        for (FieldNode f : node.fields) {
            if (f.desc.equals(desc) && (f.access & Opcodes.ACC_STATIC) != 0 && (f.access & Opcodes.ACC_SYNTHETIC) != 0) {
                if (found == null || f.name.equals("$VALUES")) {
                    found = f;
                }
            }
        }
        return found;
    }

    /** create(String, A, B...) 对应的构造器 (String, int, A, B...)V。 */
    static String constructorDesc(Type[] createArgs) {
        Type[] ctor = new Type[createArgs.length + 1];
        ctor[0] = STRING;
        ctor[1] = Type.INT_TYPE;
        System.arraycopy(createArgs, 1, ctor, 2, createArgs.length - 1);
        return Type.getMethodDescriptor(Type.VOID_TYPE, ctor);
    }

    private static void rewrite(ClassNode node, MethodNode m, Type[] args, String ctorDesc, FieldNode values) {
        String owner = node.name;
        m.access = (m.access | Opcodes.ACC_SYNCHRONIZED) & ~(Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT);
        m.instructions.clear();
        m.tryCatchBlocks = new ArrayList<>();
        m.localVariables = null;
        m.visibleLocalVariableAnnotations = null;
        m.invisibleLocalVariableAnnotations = null;

        int argSlots = 0;
        for (Type a : args) {
            argSlots += a.getSize();
        }
        int created = argSlots;
        boolean frames = (node.version & 0xFFFF) >= Opcodes.V1_6;

        // Object old = ExtensibleEnums.existing(name, $VALUES); if (old != null) return (E) old;
        Label fresh = new Label();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitFieldInsn(Opcodes.GETSTATIC, owner, values.name, values.desc);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "existing", EXISTING_DESC, false);
        m.visitInsn(Opcodes.DUP);
        m.visitJumpInsn(Opcodes.IFNULL, fresh);
        m.visitTypeInsn(Opcodes.CHECKCAST, owner);
        m.visitInsn(Opcodes.ARETURN);
        m.visitLabel(fresh);
        if (frames) {
            // 局部变量只有参数（这是方法里第一个帧），栈上是 existing 返回的 null
            m.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Object"});
        }
        m.visitInsn(Opcodes.POP);

        // E e = new E(name, $VALUES.length, args...);
        m.visitTypeInsn(Opcodes.NEW, owner);
        m.visitInsn(Opcodes.DUP);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitFieldInsn(Opcodes.GETSTATIC, owner, values.name, values.desc);
        m.visitInsn(Opcodes.ARRAYLENGTH);
        int slot = 1;
        int extraSlots = 0;
        for (int i = 1; i < args.length; i++) {
            m.visitVarInsn(args[i].getOpcode(Opcodes.ILOAD), slot);
            slot += args[i].getSize();
            extraSlots += args[i].getSize();
        }
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", ctorDesc, false);
        m.visitVarInsn(Opcodes.ASTORE, created);

        // $VALUES = (E[]) ExtensibleEnums.append($VALUES, e);
        m.visitFieldInsn(Opcodes.GETSTATIC, owner, values.name, values.desc);
        m.visitVarInsn(Opcodes.ALOAD, created);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "append", APPEND_DESC, false);
        m.visitTypeInsn(Opcodes.CHECKCAST, values.desc);
        m.visitFieldInsn(Opcodes.PUTSTATIC, owner, values.name, values.desc);

        // ExtensibleEnums.reset(E.class); e.init(); return e;
        m.visitLdcInsn(Type.getObjectType(owner));
        m.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "reset", RESET_DESC, false);
        m.visitVarInsn(Opcodes.ALOAD, created);
        m.visitMethodInsn(Opcodes.INVOKEINTERFACE, MARKER, "init", "()V", true);
        m.visitVarInsn(Opcodes.ALOAD, created);
        m.visitInsn(Opcodes.ARETURN);

        m.maxStack = 4 + extraSlots;
        m.maxLocals = argSlots + 1;
    }
}
