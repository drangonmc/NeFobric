package net.minecraftforge.eventbus.api;

/**
 * 监听器的优先级，按声明顺序执行。也作为“阶段标记”插在监听器列表里，执行到它时更新事件的当前阶段。
 */
public enum EventPriority implements IEventListener {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST,
    MONITOR;

    @Override
    public void invoke(Event event) {
        event.setPhase(this);
    }
}
