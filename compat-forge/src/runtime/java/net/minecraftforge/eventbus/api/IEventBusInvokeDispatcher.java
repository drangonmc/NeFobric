package net.minecraftforge.eventbus.api;

/** 包装每一次监听器调用，例如在调用前后切换“当前模组”。 */
public interface IEventBusInvokeDispatcher {
    void invoke(IEventListener listener, Event event);
}
