package dev.nefobric.loader.mixin;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mixin 和 MixinExtras 的全局属性（“黑板”）。LaunchWrapper 和 ModLauncher 用它们自己的黑板，
 * NeFobric 用一张进程内的表，和 Fabric Loader 一样。
 */
public final class NeFobricGlobalPropertyService implements IGlobalPropertyService {
    private static final Map<String, Object> PROPERTIES = new ConcurrentHashMap<>();

    private record Key(String name) implements IPropertyKey {
        @Override
        public String toString() {
            return name;
        }
    }

    @Override
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key) {
        return (T) PROPERTIES.get(key.toString());
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        if (value == null) {
            PROPERTIES.remove(key.toString());
        } else {
            PROPERTIES.put(key.toString(), value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        Object v = PROPERTIES.get(key.toString());
        return v != null ? (T) v : defaultValue;
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        Object v = PROPERTIES.get(key.toString());
        return v != null ? v.toString() : defaultValue;
    }
}
