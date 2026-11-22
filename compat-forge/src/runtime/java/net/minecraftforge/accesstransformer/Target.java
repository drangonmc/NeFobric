package net.minecraftforge.accesstransformer;

import org.objectweb.asm.Type;

import java.util.Objects;
import java.util.Set;

/**
 * 一条访问变换指向的东西：类本身、字段、方法，或者某个类的全部字段/方法。
 *
 * @param <T> 应用到的 ASM 节点类型
 */
public abstract class Target<T> {
    private final String className;
    private final Type type;

    /** @param className 点分形式的类名（{@code net.minecraft.world.level.Level}） */
    public Target(String className) {
        this.className = className;
        this.type = Type.getObjectType(className.replace('.', '/'));
    }

    public String getClassName() {
        return className;
    }

    public Type getASMType() {
        return type;
    }

    public abstract TargetType getType();

    /** 类本身是类名，字段是字段名，方法是名字加描述符，通配是 {@code *} 或 {@code *()}。 */
    public abstract String targetName();

    /**
     * 把修改应用到节点上。
     *
     * @param privateChanged 从 private 变成非 private 的实例方法（名字加描述符），之后要把类里对它们的
     *                       INVOKESPECIAL 改成虚调用
     */
    public abstract void apply(T node, AccessTransformer.Modifier targetAccess,
                               AccessTransformer.FinalState targetFinalState, Set<String> privateChanged);

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Target<?> other)) {
            return false;
        }
        return getType() == other.getType() && className.equals(other.className)
                && targetName().equals(other.targetName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getType(), className, targetName());
    }

    @Override
    public String toString() {
        return getType() + " " + className + (getType() == TargetType.CLASS ? "" : " " + targetName());
    }
}
