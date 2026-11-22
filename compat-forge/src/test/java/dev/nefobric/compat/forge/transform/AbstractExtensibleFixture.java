package dev.nefobric.compat.forge.transform;

import net.minecraftforge.common.IExtensibleEnum;

/**
 * 带抽象方法的可扩展枚举：new 不出实例，变换器不改写它，但类要能正常加载。
 * 单独一个顶层类，这样它自己是嵌套主类，测试里和常量体子类（$1）一起换个类加载器加载时私有构造器还能访问。
 */
public enum AbstractExtensibleFixture implements IExtensibleEnum {
    A {
        @Override
        int value() {
            return 1;
        }
    };

    abstract int value();

    public static AbstractExtensibleFixture create(String name) {
        throw new IllegalStateException("Enum not extended");
    }
}
