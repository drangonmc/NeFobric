package net.minecraftforge.common.util;

import java.util.Objects;

/** 结果不为 null 的 {@link Lazy}。 */
public interface NonNullLazy<T> extends NonNullSupplier<T> {
    static <T> NonNullLazy<T> of(NonNullSupplier<T> supplier) {
        Lazy<T> lazy = Lazy.of(supplier::get);
        return () -> Objects.requireNonNull(lazy.get());
    }

    static <T> NonNullLazy<T> concurrentOf(NonNullSupplier<T> supplier) {
        Lazy<T> lazy = Lazy.concurrentOf(supplier::get);
        return () -> Objects.requireNonNull(lazy.get());
    }
}
