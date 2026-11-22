package net.minecraftforge.eventbus.api;

import java.lang.reflect.Type;

/** 带泛型参数的事件基类，例如 {@code AttachCapabilitiesEvent<Entity>}。 */
public class GenericEvent<T> extends Event implements IGenericEvent<T> {
    private Class<T> type;

    public GenericEvent() {
    }

    protected GenericEvent(Class<T> type) {
        this.type = type;
    }

    @Override
    public Type getGenericType() {
        return type;
    }
}
