package net.minecraftforge.accesstransformer;

import org.objectweb.asm.tree.FieldNode;

import java.util.Set;

/** 一个字段。 */
public class FieldTarget extends Target<FieldNode> {
    private final String fieldName;

    public FieldTarget(String className, String fieldName) {
        super(className);
        this.fieldName = fieldName;
    }

    @Override
    public TargetType getType() {
        return TargetType.FIELD;
    }

    @Override
    public String targetName() {
        return fieldName;
    }

    @Override
    public void apply(FieldNode node, AccessTransformer.Modifier targetAccess,
                      AccessTransformer.FinalState targetFinalState, Set<String> privateChanged) {
        node.access = targetFinalState.mergeWith(targetAccess.mergeWith(node.access));
    }
}
