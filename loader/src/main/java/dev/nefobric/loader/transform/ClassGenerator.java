package dev.nefobric.loader.transform;

/** 在任何 jar 里都找不到的类，交给它生成（Mixin 运行时生成的合成类）。 */
@FunctionalInterface
public interface ClassGenerator {
    /** @return 类的字节码；不是由它负责的类返回 {@code null} */
    byte[] generate(String className);
}
