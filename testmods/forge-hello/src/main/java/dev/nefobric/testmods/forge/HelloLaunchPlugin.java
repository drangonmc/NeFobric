package dev.nefobric.testmods.forge;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.EnumSet;

/** 把 PluginTarget 里的字符串常量 "original" 换掉，要求重算栈帧。 */
final class HelloLaunchPlugin implements ILaunchPluginService {
    private static final String TARGET = "dev.nefobric.testmods.forge.PluginTarget";

    @Override
    public String name() {
        return "forge_hello";
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        return classType.getClassName().equals(TARGET) ? EnumSet.of(Phase.AFTER) : EnumSet.noneOf(Phase.class);
    }

    @Override
    public int processClassWithFlags(Phase phase, ClassNode classNode, Type classType, String reason) {
        for (MethodNode m : classNode.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof LdcInsnNode ldc && "original".equals(ldc.cst)) {
                    ldc.cst = "patched";
                }
            }
        }
        return ComputeFlags.COMPUTE_FRAMES;
    }
}
