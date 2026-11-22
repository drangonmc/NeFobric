package dev.nefobric.compat.fabric.impl;

import net.fabricmc.loader.api.ObjectShare;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** 与 Fabric Loader 相同的语义：键必须是 {@code modid:subkey}，值不能为 null，等待者在值第一次出现时收到回调。 */
final class ObjectShareImpl implements ObjectShare {
    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, List<BiConsumer<String, Object>>> pendingMap = new HashMap<>();

    @Override
    public synchronized Object get(String key) {
        validateKey(key);
        return values.get(key);
    }

    @Override
    public void whenAvailable(String key, BiConsumer<String, Object> consumer) {
        validateKey(key);
        Object value;
        synchronized (this) {
            value = values.get(key);
            if (value == null) {
                pendingMap.computeIfAbsent(key, ignore -> new ArrayList<>()).add(consumer);
                return;
            }
        }
        consumer.accept(key, value);
    }

    @Override
    public Object put(String key, Object value) {
        validateKey(key);
        Objects.requireNonNull(value, "null value");
        List<BiConsumer<String, Object>> pending;
        Object prev;
        synchronized (this) {
            prev = values.put(key, value);
            if (prev != null) {
                return prev;
            }
            pending = pendingMap.remove(key);
        }
        if (pending != null) {
            invokePending(key, value, pending);
        }
        return null;
    }

    @Override
    public Object putIfAbsent(String key, Object value) {
        validateKey(key);
        Objects.requireNonNull(value, "null value");
        List<BiConsumer<String, Object>> pending;
        synchronized (this) {
            Object prev = values.putIfAbsent(key, value);
            if (prev != null) {
                return prev;
            }
            pending = pendingMap.remove(key);
        }
        if (pending != null) {
            invokePending(key, value, pending);
        }
        return null;
    }

    @Override
    public synchronized Object remove(String key) {
        validateKey(key);
        return values.remove(key);
    }

    private static void invokePending(String key, Object value, List<BiConsumer<String, Object>> pending) {
        for (BiConsumer<String, Object> consumer : pending) {
            consumer.accept(key, value);
        }
    }

    private static void validateKey(String key) {
        Objects.requireNonNull(key, "null key");
        int pos = key.indexOf(':');
        if (pos <= 0 || pos >= key.length() - 1) {
            throw new IllegalArgumentException("invalid key, must be modid:subkey");
        }
    }
}
