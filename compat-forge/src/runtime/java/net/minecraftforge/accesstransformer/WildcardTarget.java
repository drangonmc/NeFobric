package net.minecraftforge.accesstransformer;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;

/** 一个类的全部字段（{@code *}）或全部方法（{@code *()}，不含静态初始化块）。 */
public class WildcardTarget extends Target<ClassNode> {
    private final TargetType type;

    public WildcardTarget(String className, boolean isMethod) {
        super(className);
        this.type = isMethod ? TargetType.METHOD : TargetType.FIELD;
    }

    @Override
    public TargetType getType() {
        return type;
    }

    @Override
    public String targetName() {
        return type == TargetType.METHOD ? "*()" : "*";
    }

    @Override
    public void apply(ClassNode node, AccessTransformer.Modifier targetAccess,
                      AccessTransformer.FinalState targetFinalState, Set<String> privateChanged) {
        if (type == TargetType.FIELD) {
            for (FieldNode f : node.fields) {
                new FieldTarget(getClassName(), f.name).apply(f, targetAccess, targetFinalState, privateChanged);
            }
        } else {
            for (MethodNode m : node.methods) {
                if (!m.name.equals("<clinit>")) {
                    new MethodTarget(getClassName(), m.name, m.desc).apply(m, targetAccess, targetFinalState,
                            privateChanged);
                }
            }
        }
    }
}
