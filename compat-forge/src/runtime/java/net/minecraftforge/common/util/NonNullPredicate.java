package net.minecraftforge.common.util;

import org.jetbrains.annotations.NotNull;

/** 参数不会是 null 的 {@link java.util.function.Predicate}。 */
@FunctionalInterface
public interface NonNullPredicate<T> {
    boolean test(@NotNull T t);
}
