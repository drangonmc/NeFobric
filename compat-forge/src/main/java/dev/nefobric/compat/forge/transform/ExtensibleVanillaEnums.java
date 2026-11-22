package dev.nefobric.compat.forge.transform;

import dev.nefobric.loader.classloading.ClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * GAME_PATCH 阶段：重现 Forge 对原版枚举的补丁里“可扩展”的那部分，让模组能调 {@code Rarity.create(...)} 这类方法。
 *
 * <p>对列出的原版枚举：加上 {@code implements IExtensibleEnum}，并为每个（非合成的）构造器
 * {@code (String, int, A, B...)} 加一个 {@code public static E create(String, A, B...)}，方法体只是抛异常，
 * 由 ACCESS 阶段的 {@link EnumExtender} 改写成真正追加常量的代码。这样得到的 create 描述符和 Forge 补丁里的一致。</p>
 *
 * <p>只列出“加上 create 就完整”的枚举。Forge 对其他枚举还加了新的构造器或 init() 里的登记
 * （Rarity 的 UnaryOperator&lt;Style&gt; 版 create、MobCategory.byName、RecipeBookType 的配方书设置、
 * Raid.RaiderType 改 public 并刷新缓存、FireworkRocketItem.Shape 的存档名、EnchantmentCategory、
 * SpawnPlacements.Type、HumanoidModel.ArmPose、BiomeSpecialEffects.GrassColorModifier、ItemDisplayContext，
 * 以及 NeoForge 的 DamageScaling、DeathMessageType、DamageEffects），这些要配合对着原版编译的运行时代码，以后再加。</p>
 */
public final class ExtensibleVanillaEnums implements ClassTransformer {
    /** 运行时命名（Mojang 类名）下的二进制类名。 */
    public static final Set<String> DEFAULT_TARGETS = Set.of(
            "net.minecraft.world.item.Rarity",
            "net.minecraft.world.entity.MobCategory",
            "net.minecraft.world.level.pathfinder.BlockPathTypes");

    private final Set<String> targets;

    public ExtensibleVanillaEnums() {
        this(DEFAULT_TARGETS);
    }

    ExtensibleVanillaEnums(Set<String> targets) {
        this.targets = targets;
    }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        if (!targets.contains(className)) {
            return bytes;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (!patch(node)) {
            return bytes;
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    /** @return 是否有改动；不是普通枚举（或是带常量体的抽象枚举）时不动 */
    static boolean patch(ClassNode node) {
        if ((node.access & Opcodes.ACC_ENUM) == 0 || (node.access & Opcodes.ACC_ABSTRACT) != 0) {
            return false;
        }
        Type self = Type.getObjectType(node.name);
        List<MethodNode> added = new ArrayList<>();
        for (MethodNode ctor : node.methods) {
            if (!ctor.name.equals("<init>") || (ctor.access & Opcodes.ACC_SYNTHETIC) != 0) {
                continue;
            }
            Type[] ctorArgs = Type.getArgumentTypes(ctor.desc);
            if (ctorArgs.length < 2 || !ctorArgs[0].equals(Type.getType(String.class)) || ctorArgs[1] != Type.INT_TYPE) {
                continue;
            }
            Type[] createArgs = new Type[ctorArgs.length - 1];
            createArgs[0] = ctorArgs[0];
            System.arraycopy(ctorArgs, 2, createArgs, 1, ctorArgs.length - 2);
            String desc = Type.getMethodDescriptor(self, createArgs);
            boolean exists = node.methods.stream().anyMatch(m -> m.name.equals("create") && m.desc.equals(desc))
                    || added.stream().anyMatch(m -> m.desc.equals(desc));
            if (!exists) {
                added.add(stub(desc, createArgs));
            }
        }
        if (added.isEmpty()) {
            return false;
        }
        node.methods.addAll(added);
        if (!node.interfaces.contains(EnumExtender.MARKER)) {
            node.interfaces.add(EnumExtender.MARKER);
            if (node.signature != null) {
                // 泛型签名里也要有这个接口，否则反射看到的接口列表和实际不一致
                node.signature = node.signature + "L" + EnumExtender.MARKER + ";";
            }
        }
        return true;
    }

    /** {@code public static E create(...) { throw new IllegalStateException("Enum not extended"); }} */
    private static MethodNode stub(String desc, Type[] args) {
        MethodNode m = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create", desc, null, null);
        m.visitCode();
        m.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
        m.visitInsn(Opcodes.DUP);
        m.visitLdcInsn("Enum not extended");
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false);
        m.visitInsn(Opcodes.ATHROW);
        m.maxStack = 3;
        m.maxLocals = Arrays.stream(args).mapToInt(Type::getSize).sum();
        m.visitEnd();
        return m;
    }
}
