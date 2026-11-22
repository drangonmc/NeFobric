package net.minecraftforge.forgespi.language;

/** 语言提供者收到的生命周期事件。 */
public interface ILifecycleEvent<R extends ILifecycleEvent<?>> {
    @SuppressWarnings("unchecked")
    default R concrete() {
        return (R) this;
    }
}
