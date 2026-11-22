package net.minecraftforge.common.util;

import org.jetbrains.annotations.NotNull;

/** 不会返回 null 的 {@link java.util.function.Supplier}。 */
@FunctionalInterface
public interface NonNullSupplier<T> {
    @NotNull
    T get();
}
