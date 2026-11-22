package net.minecraftforge.common.util;

import org.jetbrains.annotations.NotNull;

/** 参数不会是 null 的 {@link java.util.function.Consumer}。 */
@FunctionalInterface
public interface NonNullConsumer<T> {
    void accept(@NotNull T t);
}
