package net.minecraftforge.common.util;

import java.util.function.Supplier;

/** 第一次 {@link #get()} 时才计算、之后一直返回同一个值的 {@link Supplier}。 */
public interface Lazy<T> extends Supplier<T> {
    /** 非线程安全的版本。 */
    static <T> Lazy<T> of(Supplier<T> supplier) {
        return new Fast<>(supplier);
    }

    /** 线程安全的版本：多个线程同时第一次调用时只计算一次。 */
    static <T> Lazy<T> concurrentOf(Supplier<T> supplier) {
        return new Concurrent<>(supplier);
    }

    final class Fast<T> implements Lazy<T> {
        private Supplier<T> supplier;
        private T instance;

        private Fast(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T get() {
            if (supplier != null) {
                instance = supplier.get();
                supplier = null;
            }
            return instance;
        }
    }

    final class Concurrent<T> implements Lazy<T> {
        private final Object lock = new Object();
        private volatile Supplier<T> supplier;
        private volatile T instance;

        private Concurrent(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T get() {
            if (supplier != null) {
                synchronized (lock) {
                    Supplier<T> s = supplier;
                    if (s != null) {
                        instance = s.get();
                        supplier = null;
                    }
                }
            }
            return instance;
        }
    }
}
