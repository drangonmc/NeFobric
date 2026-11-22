package net.minecraftforge.eventbus.api;

import net.minecraftforge.eventbus.ListenerList;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 事件类到监听器列表的对应关系，以及按注解判断事件是否可取消、是否带结果。 */
public class EventListenerHelper {
    private static final Map<Class<?>, ListenerList> LISTS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> CANCELABLE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> HAS_RESULT = new ConcurrentHashMap<>();
    private static final ListenerList ROOT = new ListenerList();

    public static ListenerList getListenerList(Class<?> eventClass) {
        return listFor(eventClass);
    }

    static ListenerList listFor(Class<?> eventClass) {
        if (eventClass == Event.class) {
            return ROOT;
        }
        ListenerList l = LISTS.get(eventClass);
        if (l != null) {
            return l;
        }
        // 不在 computeIfAbsent 里递归，避免同一张表的嵌套修改
        ListenerList parent = listFor(eventClass.getSuperclass());
        return LISTS.computeIfAbsent(eventClass, k -> new ListenerList(parent));
    }

    public static boolean isCancelable(Class<?> eventClass) {
        return annotated(eventClass, Cancelable.class, CANCELABLE);
    }

    static boolean hasResult(Class<?> eventClass) {
        return annotated(eventClass, Event.HasResult.class, HAS_RESULT);
    }

    private static boolean annotated(Class<?> c, Class<? extends Annotation> a, Map<Class<?>, Boolean> cache) {
        if (c == null || c == Event.class || c == Object.class) {
            return false;
        }
        Boolean cached = cache.get(c);
        if (cached != null) {
            return cached;
        }
        boolean v = c.isAnnotationPresent(a) || annotated(c.getSuperclass(), a, cache);
        cache.put(c, v);
        return v;
    }
}
