package net.minecraftforge.accesstransformer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;

import java.util.Set;

/** 类本身。内部类的访问级别同时记在内部类表里，两处都改。 */
public class ClassTarget extends Target<ClassNode> {
    public ClassTarget(String className) {
        super(className);
    }

    @Override
    public TargetType getType() {
        return TargetType.CLASS;
    }

    @Override
    public String targetName() {
        return getClassName();
    }

    @Override
    public void apply(ClassNode node, AccessTransformer.Modifier targetAccess,
                      AccessTransformer.FinalState targetFinalState, Set<String> privateChanged) {
        int access = targetFinalState.mergeWith(targetAccess.mergeWith(node.access));
        // class 文件的 access 只能是 public 或包级：protected 记成 public，private 记成包级
        int file = access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC);
        if ((access & Opcodes.ACC_PROTECTED) != 0) {
            file |= Opcodes.ACC_PUBLIC;
        }
        node.access = file;
        if (node.innerClasses != null) {
            for (InnerClassNode inner : node.innerClasses) {
                if (inner.name.equals(node.name)) {
                    inner.access = targetFinalState.mergeWith(targetAccess.mergeWith(inner.access));
                }
            }
        }
    }
}
