package net.minecraftforge.fml;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class OptionalMod<T> {
    private final String modId;
    private T value;
    private boolean searched;

    public static <M> OptionalMod<M> of(final String modId) {
        return new OptionalMod<>(modId);
    }

    private static final OptionalMod<?> EMPTY = new OptionalMod<>(true);

    private OptionalMod(boolean empty) {
        this.modId = "";
        this.searched = empty;
    }

    private OptionalMod(String modId) {
        this.modId = modId;
    }

    @SuppressWarnings("unchecked")
    private static <T> OptionalMod<T> empty() {
        return (OptionalMod<T>) EMPTY;
    }

    private T getValue() {
        if (!searched) {
            value = ModList.get().<T>getModObjectById(modId).orElse(null);
            searched = true;
        }
        return value;
    }

    public T get() {
        T v = getValue();
        if (v == null) {
            throw new NoSuchElementException("No value present");
        }
        return v;
    }

    public String getModId() {
        return modId;
    }

    public boolean isPresent() {
        return getValue() != null;
    }

    public void ifPresent(Consumer<? super T> consumer) {
        if (isPresent()) {
            consumer.accept(get());
        }
    }

    public OptionalMod<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        if (!isPresent()) {
            return this;
        }
        return predicate.test(get()) ? this : empty();
    }

    public <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        return isPresent() ? Optional.ofNullable(mapper.apply(get())) : Optional.empty();
    }

    public <U> Optional<U> flatMap(Function<? super T, Optional<U>> mapper) {
        Objects.requireNonNull(mapper);
        return isPresent() ? Objects.requireNonNull(mapper.apply(get())) : Optional.empty();
    }

    public T orElse(T other) {
        return isPresent() ? get() : other;
    }

    public T orElseGet(Supplier<? extends T> other) {
        return isPresent() ? get() : other.get();
    }

    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (isPresent()) {
            return get();
        }
        throw exceptionSupplier.get();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof OptionalMod<?> other && Objects.equals(getValue(), other.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getValue());
    }
}
