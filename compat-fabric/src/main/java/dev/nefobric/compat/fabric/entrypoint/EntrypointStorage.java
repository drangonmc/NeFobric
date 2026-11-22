package dev.nefobric.compat.fabric.entrypoint;

import dev.nefobric.compat.fabric.impl.ModContainerImpl;
import dev.nefobric.compat.fabric.metadata.FabricModMetadata;
import dev.nefobric.compat.fabric.metadata.FabricModMetadata.EntrypointMetadata;
import dev.nefobric.loader.util.Log;
import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 入口表。行为对齐 Fabric Loader 的 EntrypointStorage / EntrypointUtils：
 * <ul>
 *   <li>每条入口按类型缓存一个实例；同一个类写在 main 和 client 两个键下是两条入口、两个实例；</li>
 *   <li>{@link #getEntrypointContainers} 返回的容器在 {@code getEntrypoint()} 时才创建实例；</li>
 *   <li>{@link #invoke} 某个模组出错后继续调用其余模组，最后把所有错误合成一个 {@link EntrypointException} 抛出；</li>
 *   <li>language adapter 来自各模组的 languageAdapters，第一次使用时用游戏类加载器创建。</li>
 * </ul>
 */
public final class EntrypointStorage {
    /** 一次调用中出错的入口。 */
    public record Failure(ModContainerImpl mod, String definition, Throwable error) {
    }

    private final Map<String, List<Entry>> entryMap;
    private final Map<String, String> adapterClasses;
    private final Map<String, LanguageAdapter> adapters = new ConcurrentHashMap<>();
    private final ClassLoader classLoader;

    private EntrypointStorage(Map<String, List<Entry>> entryMap, Map<String, String> adapterClasses, ClassLoader classLoader) {
        this.entryMap = entryMap;
        this.adapterClasses = adapterClasses;
        this.classLoader = classLoader;
        adapters.put("default", DefaultLanguageAdapter.INSTANCE);
    }

    public static EntrypointStorage create(List<ModContainerImpl> mods, ClassLoader classLoader) {
        Map<String, String> adapterClasses = new LinkedHashMap<>();
        Map<String, List<Entry>> entryMap = new LinkedHashMap<>();
        EntrypointStorage storage = new EntrypointStorage(entryMap, adapterClasses, classLoader);
        for (ModContainerImpl mod : mods) {
            if (!(mod.getMetadata() instanceof FabricModMetadata meta)) {
                continue;
            }
            meta.languageAdapters().forEach((key, cls) -> {
                String prev = adapterClasses.putIfAbsent(key, cls);
                if (prev != null && !prev.equals(cls)) {
                    Log.warn("Duplicate language adapter key '" + key + "' from mod " + meta.getId() + " (" + cls
                            + "), keeping " + prev);
                }
            });
            for (Map.Entry<String, List<EntrypointMetadata>> e : meta.entrypoints().entrySet()) {
                for (EntrypointMetadata ep : e.getValue()) {
                    entryMap.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(storage.new Entry(mod, ep));
                }
            }
        }
        return storage;
    }

    public boolean hasEntrypoints(String key) {
        return entryMap.containsKey(key);
    }

    LanguageAdapter adapter(String name, ModContainer requester) throws Exception {
        LanguageAdapter a = adapters.get(name);
        if (a != null) {
            return a;
        }
        String cls = adapterClasses.get(name);
        if (cls == null) {
            throw new IllegalArgumentException("Could not find adapter '" + name + "' (mod " + requester.getMetadata().getId() + ")!");
        }
        synchronized (adapters) {
            a = adapters.get(name);
            if (a == null) {
                Class<?> c = Class.forName(cls, true, classLoader);
                a = (LanguageAdapter) c.getDeclaredConstructor().newInstance();
                adapters.put(name, a);
            }
        }
        return a;
    }

    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        List<Entry> entries = entryMap.get(key);
        if (entries == null) {
            return Collections.emptyList();
        }
        EntrypointException exception = null;
        List<T> results = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            try {
                T result = entry.getOrCreate(type);
                if (result != null) {
                    results.add(result);
                }
            } catch (Throwable t) {
                if (exception == null) {
                    exception = new EntrypointException(key, entry.mod.getMetadata().getId(), t);
                } else {
                    exception.addSuppressed(t);
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
        return results;
    }

    public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        List<Entry> entries = entryMap.get(key);
        if (entries == null) {
            return Collections.emptyList();
        }
        List<EntrypointContainer<T>> results = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            results.add(new Container<>(key, type, entry));
        }
        return results;
    }

    public <T> void invoke(String key, Class<T> type, Consumer<? super T> invoker) {
        List<Failure> failures = invokeCollecting(key, type, invoker);
        if (failures.isEmpty()) {
            return;
        }
        Failure first = failures.get(0);
        EntrypointException ex = new EntrypointException(key, first.mod().getMetadata().getId(), first.error());
        for (int i = 1; i < failures.size(); i++) {
            ex.addSuppressed(failures.get(i).error());
        }
        throw ex;
    }

    /** 依次创建并调用所有入口；出错的记下来继续，返回全部错误（按出现顺序）。 */
    public <T> List<Failure> invokeCollecting(String key, Class<T> type, Consumer<? super T> invoker) {
        List<Entry> entries = entryMap.get(key);
        if (entries == null) {
            Log.debug("No subscribers for entrypoint '" + key + "'");
            return List.of();
        }
        List<Failure> failures = new ArrayList<>();
        for (EntrypointContainer<T> container : getEntrypointContainers(key, type)) {
            Entry entry = ((Container<T>) container).entry;
            Log.debug("Fabric " + key + ": " + entry.mod.getMetadata().getId() + " -> " + entry.value);
            try {
                invoker.accept(container.getEntrypoint());
            } catch (Throwable t) {
                failures.add(new Failure(entry.mod, entry.value, unwrap(t)));
            }
        }
        return failures;
    }

    private static Throwable unwrap(Throwable t) {
        // getEntrypoint() 出错时包了一层 EntrypointException，报给玩家时取里面真正的原因
        if (t instanceof EntrypointException && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof net.fabricmc.loader.api.LanguageAdapterException && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof ExceptionInInitializerError && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    final class Entry {
        final ModContainerImpl mod;
        final String adapterName;
        final String value;
        private final Map<Class<?>, Object> instanceMap = new IdentityHashMap<>(1);

        Entry(ModContainerImpl mod, EntrypointMetadata metadata) {
            this.mod = mod;
            this.adapterName = metadata.adapter();
            this.value = metadata.value();
        }

        @SuppressWarnings("unchecked")
        synchronized <T> T getOrCreate(Class<T> type) throws Exception {
            // 允许重入：入口在构造时查询自己所在的键
            Object ret = instanceMap.get(type);
            if (ret == null) {
                ret = adapter(adapterName, mod).create(mod, value, type);
                Object prev = instanceMap.putIfAbsent(type, ret);
                if (prev != null) {
                    ret = prev;
                }
            }
            return (T) ret;
        }

        @Override
        public String toString() {
            return mod.getMetadata().getId() + "->(" + adapterName + ")" + value;
        }
    }

    private static final class Container<T> implements EntrypointContainer<T> {
        private final String key;
        private final Class<T> type;
        final Entry entry;
        private T instance;

        Container(String key, Class<T> type, Entry entry) {
            this.key = key;
            this.type = type;
            this.entry = entry;
        }

        @Override
        public synchronized T getEntrypoint() {
            if (instance == null) {
                try {
                    instance = entry.getOrCreate(type);
                } catch (Throwable t) {
                    throw new EntrypointException(key, entry.mod.getMetadata().getId(), t);
                }
            }
            return instance;
        }

        @Override
        public ModContainer getProvider() {
            return entry.mod;
        }

        @Override
        public String getDefinition() {
            return entry.value;
        }
    }
}
