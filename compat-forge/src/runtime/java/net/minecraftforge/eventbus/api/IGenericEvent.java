package net.minecraftforge.eventbus.api;

import java.lang.reflect.Type;

/** 带泛型参数的事件，监听器可以按泛型参数过滤。 */
public interface IGenericEvent<T> {
    Type getGenericType();
}
