package net.fabricmc.loader.api;

import java.util.function.BiConsumer;

/** 模组之间按字符串键共享对象，键的格式是 {@code modid:name}。 */
public interface ObjectShare {
    Object get(String key);

    void whenAvailable(String key, BiConsumer<String, Object> consumer);

    Object put(String key, Object value);

    Object putIfAbsent(String key, Object value);

    Object remove(String key);
}
