package net.minecraftforge.accesstransformer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;

/** 一个方法（名字加描述符）。 */
public class MethodTarget extends Target<MethodNode> {
    private final String methodName;
    private final String methodDescriptor;

    public MethodTarget(String className, String methodName, String methodDescriptor) {
        super(className);
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMethodDescriptor() {
        return methodDescriptor;
    }

    @Override
    public TargetType getType() {
        return TargetType.METHOD;
    }

    @Override
    public String targetName() {
        return methodName + methodDescriptor;
    }

    @Override
    public void apply(MethodNode node, AccessTransformer.Modifier targetAccess,
                      AccessTransformer.FinalState targetFinalState, Set<String> privateChanged) {
        int before = node.access;
        node.access = targetFinalState.mergeWith(targetAccess.mergeWith(before));
        if ((before & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == Opcodes.ACC_PRIVATE
                && (node.access & Opcodes.ACC_PRIVATE) == 0 && !node.name.startsWith("<")) {
            privateChanged.add(node.name + node.desc);
        }
    }
}
