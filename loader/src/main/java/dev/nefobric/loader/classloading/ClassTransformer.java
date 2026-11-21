package dev.nefobric.loader.classloading;

/**
 * 类定义之前对字节码的变换。Alpha 只有游戏入口钩子一个变换器；之后的 Mixin、访问变换、重映射、
 * Forge 补丁重现都会作为变换器接到同一条流水线上。
 */
@FunctionalInterface
public interface ClassTransformer {
    /**
     * @param className 二进制类名，例如 {@code net.minecraft.client.main.Main}
     * @param bytes     当前的字节码
     * @return 变换后的字节码；不需要修改时原样返回
     */
    byte[] transform(String className, byte[] bytes);
}
