package net.minecraftforge.common.capabilities;

import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 一种能力（例如物品栏、流体槽、能量）。用 {@link CapabilityManager#get(CapabilityToken)} 拿到；
 * 同一个接口在整个游戏里只有一个 Capability 对象，所以可以用 {@code ==} 比较。
 * 实现它的接口被注册（{@link RegisterCapabilitiesEvent}）之后 {@link #isRegistered()} 为 true。
 */
public final class Capability<T> {
    private final String name;
    private List<Consumer<Capability<T>>> listeners = new ArrayList<>();

    Capability(String name) {
        this.name = name;
    }

    /** 能力接口的内部名，例如 {@code net/minecraftforge/items/IItemHandler}。 */
    public String getName() {
        return name;
    }

    /** {@code toCheck} 就是这个能力时返回 {@code inst}，否则返回空：能力提供者的常见写法。 */
    public @NotNull <R> LazyOptional<R> orEmpty(Capability<R> toCheck, LazyOptional<T> inst) {
        return this == toCheck ? inst.cast() : LazyOptional.empty();
    }

    public boolean isRegistered() {
        return listeners == null;
    }

    /** 注册时回调；已经注册过的立刻回调。 */
    public synchronized Capability<T> addListener(Consumer<Capability<T>> listener) {
        if (isRegistered()) {
            listener.accept(this);
        } else {
            listeners.add(listener);
        }
        return this;
    }

    void onRegister() {
        List<Consumer<Capability<T>>> pending;
        synchronized (this) {
            pending = listeners;
            listeners = null;
        }
        pending.forEach(l -> l.accept(this));
    }

    @Override
    public String toString() {
        return "Capability[" + name + "]";
    }
}
