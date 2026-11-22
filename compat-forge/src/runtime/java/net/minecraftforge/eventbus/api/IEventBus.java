package net.minecraftforge.eventbus.api;

import java.util.function.Consumer;

/** 事件总线。Forge 有一条全局总线（{@code MinecraftForge.EVENT_BUS}），每个模组还有自己的模组总线。 */
public interface IEventBus {
    /** 注册一个对象（它的非静态 {@link SubscribeEvent} 方法）或一个类（它的静态 {@link SubscribeEvent} 方法）。 */
    void register(Object target);

    <T extends Event> void addListener(Consumer<T> consumer);

    <T extends Event> void addListener(EventPriority priority, Consumer<T> consumer);

    <T extends Event> void addListener(EventPriority priority, boolean receiveCancelled, Consumer<T> consumer);

    <T extends Event> void addListener(EventPriority priority, boolean receiveCancelled, Class<T> eventType,
                                       Consumer<T> consumer);

    <T extends GenericEvent<? extends F>, F> void addGenericListener(Class<F> genericClassFilter, Consumer<T> consumer);

    <T extends GenericEvent<? extends F>, F> void addGenericListener(Class<F> genericClassFilter, EventPriority priority,
                                                                     Consumer<T> consumer);

    <T extends GenericEvent<? extends F>, F> void addGenericListener(Class<F> genericClassFilter, EventPriority priority,
                                                                     boolean receiveCancelled, Consumer<T> consumer);

    <T extends GenericEvent<? extends F>, F> void addGenericListener(Class<F> genericClassFilter, EventPriority priority,
                                                                     boolean receiveCancelled, Class<T> eventType,
                                                                     Consumer<T> consumer);

    void unregister(Object object);

    /** @return 事件是否被取消 */
    boolean post(Event event);

    boolean post(Event event, IEventBusInvokeDispatcher wrapper);

    default <T extends Event> T fire(T event) {
        post(event);
        return event;
    }

    default <T extends Event> T fire(T event, IEventBusInvokeDispatcher wrapper) {
        post(event, wrapper);
        return event;
    }

    void shutdown();

    void start();
}
