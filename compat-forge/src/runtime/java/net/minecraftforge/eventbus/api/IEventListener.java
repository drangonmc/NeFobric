package net.minecraftforge.eventbus.api;

/** 事件总线上的一个监听器。 */
public interface IEventListener {
    void invoke(Event event);

    default String listenerName() {
        return getClass().getName();
    }
}
