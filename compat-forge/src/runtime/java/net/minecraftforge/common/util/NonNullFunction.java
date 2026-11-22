package net.minecraftforge.common.util;

import org.jetbrains.annotations.NotNull;

/** 参数和结果都不是 null 的 {@link java.util.function.Function}。 */
@FunctionalInterface
public interface NonNullFunction<T, R> {
    @NotNull
    R apply(@NotNull T t);
}
