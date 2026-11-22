package net.minecraftforge.common.capabilities;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 写成匿名子类来指明能力的类型：{@code CapabilityManager.get(new CapabilityToken<IItemHandler>(){})}。
 * 类型从匿名子类的泛型签名里读出来，只取名字。
 */
public abstract class CapabilityToken<T> {
    protected final String getType() {
        Type sup = getClass().getGenericSuperclass();
        if (!(sup instanceof ParameterizedType pt)) {
            throw new IllegalStateException("CapabilityToken must be created as an anonymous subclass with a type argument: "
                    + getClass().getName());
        }
        return internalName(pt.getActualTypeArguments()[0]);
    }

    private static String internalName(Type t) {
        if (t instanceof Class<?> c) {
            return org.objectweb.asm.Type.getInternalName(c);
        } else if (t instanceof ParameterizedType p) {
            return internalName(p.getRawType());
        } else if (t instanceof GenericArrayType a) {
            return "[" + internalName(a.getGenericComponentType());
        }
        throw new IllegalStateException("Unsupported capability type " + t);
    }

    @Override
    public String toString() {
        return "CapabilityToken[" + getType() + "]";
    }
}
