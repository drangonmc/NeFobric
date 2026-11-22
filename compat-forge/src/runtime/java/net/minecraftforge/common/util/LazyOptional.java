package net.minecraftforge.common.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 延迟取值、可以作废的 {@link Optional}：能力系统（capabilities）用它把“某个对象有没有某种能力”交给调用者，
 * 能力的提供者（例如被破坏的方块实体）消失时调用 {@link #invalidate()}，持有它的一方通过监听器得知。
 *
 * <p>值在第一次需要时由 supplier 算出并缓存；supplier 返回 null 是错误。空的 LazyOptional 全局只有一个。</p>
 */
public class LazyOptional<T> {
    private static final LazyOptional<Void> EMPTY = new LazyOptional<>(null);

    private final NonNullSupplier<T> supplier;
    private final Object lock = new Object();
    private volatile boolean resolved;
    private T value;
    private final List<NonNullConsumer<LazyOptional<T>>> listeners = new ArrayList<>();
    private volatile boolean isValid = true;

    public static <T> LazyOptional<T> of(final @Nullable NonNullSupplier<T> instanceSupplier) {
        return instanceSupplier == null ? empty() : new LazyOptional<>(instanceSupplier);
    }

    public static <T> LazyOptional<T> empty() {
        return EMPTY.cast();
    }

    @SuppressWarnings("unchecked")
    public <X> LazyOptional<X> cast() {
        return (LazyOptional<X>) this;
    }

    private LazyOptional(@Nullable NonNullSupplier<T> instanceSupplier) {
        this.supplier = instanceSupplier;
    }

    private @Nullable T getValue() {
        if (!isValid || supplier == null) {
            return null;
        }
        if (!resolved) {
            synchronized (lock) {
                if (!resolved) {
                    T v = supplier.get();
                    if (v == null) {
                        throw new IllegalStateException("Supplier should not return null value");
                    }
                    value = v;
                    resolved = true;
                }
            }
        }
        return value;
    }

    private @NotNull T getValueUnsafe() {
        T v = getValue();
        if (v == null) {
            throw new IllegalStateException("LazyOptional is empty or otherwise returned null from getValue() unexpectedly");
        }
        return v;
    }

    /** 是否有值（不会触发计算）。作废以后返回 false。 */
    public boolean isPresent() {
        return supplier != null && isValid;
    }

    public void ifPresent(NonNullConsumer<? super T> consumer) {
        Objects.requireNonNull(consumer);
        T v = getValue();
        if (isValid && v != null) {
            consumer.accept(v);
        }
    }

    /** 返回一个新的 LazyOptional，取值时才调用 mapper。 */
    public <U> LazyOptional<U> lazyMap(NonNullFunction<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        return isPresent() ? of(() -> mapper.apply(getValueUnsafe())) : empty();
    }

    public <U> Optional<U> map(NonNullFunction<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        return isPresent() ? Optional.of(mapper.apply(getValueUnsafe())) : Optional.empty();
    }

    public Optional<T> filter(NonNullPredicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        T v = getValue();
        return v != null && predicate.test(v) ? Optional.of(v) : Optional.empty();
    }

    public Optional<T> resolve() {
        return isPresent() ? Optional.of(getValueUnsafe()) : Optional.empty();
    }

    public T orElse(T other) {
        T v = getValue();
        return v != null ? v : other;
    }

    public T orElseGet(NonNullSupplier<? extends T> other) {
        T v = getValue();
        return v != null ? v : other.get();
    }

    public <X extends Throwable> T orElseThrow(NonNullSupplier<? extends X> exceptionSupplier) throws X {
        T v = getValue();
        if (v != null) {
            return v;
        }
        throw exceptionSupplier.get();
    }

    /** 作废时回调。已经是空的或已作废的，立刻回调。 */
    public void addListener(NonNullConsumer<LazyOptional<T>> listener) {
        boolean now;
        synchronized (listeners) {
            now = !isPresent();
            if (!now) {
                listeners.add(listener);
            }
        }
        if (now) {
            listener.accept(this);
        }
    }

    public void removeListener(NonNullConsumer<LazyOptional<T>> listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /** 作废：之后 {@link #isPresent()} 为 false，监听器各被回调一次。对空的 LazyOptional 无效。 */
    public void invalidate() {
        if (supplier == null || !isValid) {
            return;
        }
        List<NonNullConsumer<LazyOptional<T>>> copy;
        synchronized (listeners) {
            if (!isValid) {
                return;
            }
            isValid = false;
            copy = new ArrayList<>(listeners);
        }
        copy.forEach(l -> l.accept(this));
    }
}
