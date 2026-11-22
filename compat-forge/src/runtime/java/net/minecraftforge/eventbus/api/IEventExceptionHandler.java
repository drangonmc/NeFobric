package net.minecraftforge.eventbus.api;

/** 监听器抛出异常时调用；异常随后会继续抛给 post 的调用者。 */
public interface IEventExceptionHandler {
    void handleException(IEventBus bus, Event event, IEventListener[] listeners, int index, Throwable throwable);
}
