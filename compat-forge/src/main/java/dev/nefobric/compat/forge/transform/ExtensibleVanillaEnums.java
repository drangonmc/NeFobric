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
 * <p>列出的是只靠原版构造器就能加出 Forge 同样 create 的枚举。还没补齐的地方，要配合对着原版编译的运行时代码
 * （src/runtimeMc）以后再加：</p>
 * <ul>
 *   <li>Rarity 的 {@code create(String, UnaryOperator<Style>)} 和 getStyleModifier；</li>
 *   <li>MobCategory 的 CODEC：原版是 {@code StringRepresentable.fromEnum(MobCategory::values)}，类初始化时就固定了，
 *       新加的类别在生物群系等 JSON 里解码不出来。Forge 换成了 {@code IExtensibleEnum.createCodecForExtensibleEnum}
 *       加 {@code byName}，和 IExtensibleEnum 的 codec 方法一起补；</li>
 *   <li>其他枚举还要新的构造器或 init() 里的登记：RecipeBookType 的配方书设置、RecipeBookCategories、
 *       Raid.RaiderType 改 public 并刷新缓存、FireworkRocketItem.Shape 的存档名、SpawnPlacements.Type、
 *       HumanoidModel.ArmPose、BiomeSpecialEffects.GrassColorModifier、ItemDisplayContext，以及 NeoForge 的
 *       DamageScaling、DeathMessageType、DamageEffects；</li>
 *   <li>EnchantmentCategory（模组最常用的 create 之一）：原版是带抽象 canEnchant 的抽象枚举，Forge 加了
 *       Predicate 字段和新构造器，把 canEnchant 改成非抽象。补它时要在这里同时去掉类的 ACC_ABSTRACT，
 *       否则 {@link EnumExtender} 不会改写。</li>
 * </ul>
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
