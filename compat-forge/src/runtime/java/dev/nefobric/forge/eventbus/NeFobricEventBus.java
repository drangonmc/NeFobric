package dev.nefobric.forge.eventbus;

import net.jodah.typetools.TypeResolver;
import net.minecraftforge.eventbus.ListenerList;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventListenerHelper;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.GenericEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.IEventBusInvokeDispatcher;
import net.minecraftforge.eventbus.api.IEventExceptionHandler;
import net.minecraftforge.eventbus.api.IEventListener;
import net.minecraftforge.eventbus.api.IGenericEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * NeFobric 自己的事件总线，按 Forge EventBus 6 的公开语义实现：
 * <ul>
 *   <li>{@code register(Class)} 注册类的 public static {@code @SubscribeEvent} 方法；{@code register(对象)} 注册实例方法，
 *       父类或接口上带注解、子类重写时没带注解的方法也算。</li>
 *   <li>监听父类事件的监听器也收到子类事件；按优先级执行，同一优先级里先执行父类事件的监听器，再按注册顺序。</li>
 *   <li>已取消的事件只交给 {@code receiveCanceled} 的监听器；泛型事件按泛型参数过滤。</li>
 *   <li>Forge 在生产环境会把 {@code @SubscribeEvent} 方法改成 public，所以这里非 public 的方法也接受。</li>
 * </ul>
 */
public final class NeFobricEventBus implements IEventBus, IEventExceptionHandler {
    private static final Logger LOGGER = LogManager.getLogger("EventBus");
    private static final AtomicInteger MAX_ID = new AtomicInteger();

    private final int busID = MAX_ID.getAndIncrement();
    private final IEventExceptionHandler exceptionHandler;
    private final EnumSet<EventPriority> trackedPhases;
    private final Class<?> baseType;
    private final boolean checkTypesOnDispatch;
    private volatile boolean shutdown;
    private final Map<Object, List<IEventListener>> listeners = Collections.synchronizedMap(new IdentityHashMap<>());

    NeFobricEventBus(NeFobricBusBuilder b) {
        this.exceptionHandler = b.exceptionHandler == null ? this : b.exceptionHandler;
        this.trackedPhases = b.phases;
        this.shutdown = b.startShutdown;
        this.baseType = b.markerType;
        this.checkTypesOnDispatch = b.checkTypesOnDispatch;
    }

    public int busID() {
        return busID;
    }

    // ---- 注册 ----

    @Override
    public void register(Object target) {
        if (listeners.containsKey(target)) {
            return;
        }
        if (target instanceof Class<?> c) {
            for (Method m : allMethods(c)) {
                if (Modifier.isStatic(m.getModifiers()) && m.isAnnotationPresent(SubscribeEvent.class)) {
                    registerMethod(target, null, m);
                }
            }
        } else {
            for (Method m : instanceListenerMethods(target.getClass())) {
                registerMethod(target, target, m);
            }
        }
    }

    /** 类自己声明的方法（含非 public）加上继承来的 public 方法，按名字和参数去重。 */
    private static List<Method> allMethods(Class<?> c) {
        Map<String, Method> out = new java.util.LinkedHashMap<>();
        for (Method m : c.getDeclaredMethods()) {
            out.putIfAbsent(key(m), m);
        }
        for (Method m : c.getMethods()) {
            out.putIfAbsent(key(m), m);
        }
        return new ArrayList<>(out.values());
    }

    private static String key(Method m) {
        return m.getName() + Arrays.toString(m.getParameterTypes());
    }

    /**
     * 实例方法：子类里的方法优先；子类重写时没写注解但父类或接口里的声明带注解的，也算监听方法（Forge 同样如此）。
     */
    private static List<Method> instanceListenerMethods(Class<?> type) {
        List<Method> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>();
        Set<Class<?>> visited = new LinkedHashSet<>();
        queue.add(type);
        Map<String, Method> mostDerived = new java.util.LinkedHashMap<>();
        while (!queue.isEmpty()) {
            Class<?> c = queue.poll();
            if (c == null || c == Object.class || !visited.add(c)) {
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || m.isBridge() || m.isSynthetic()) {
                    continue;
                }
                String k = key(m);
                mostDerived.putIfAbsent(k, m);
                if (m.isAnnotationPresent(SubscribeEvent.class) && seen.add(k)) {
                    result.add(mostDerived.get(k));
                }
            }
            queue.add(c.getSuperclass());
            queue.addAll(Arrays.asList(c.getInterfaces()));
        }
        return result;
    }

    private void registerMethod(Object key, Object instance, Method method) {
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 1) {
            throw new IllegalArgumentException("Method " + method + " has @SubscribeEvent annotation. It has "
                    + params.length + " arguments, but event handler methods require a single argument only.");
        }
        Class<?> eventType = params[0];
        if (!Event.class.isAssignableFrom(eventType)) {
            throw new IllegalArgumentException("Method " + method
                    + " has @SubscribeEvent annotation, but takes an argument that is not an Event subtype : " + eventType);
        }
        if (baseType != Event.class && !baseType.isAssignableFrom(eventType)) {
            throw new IllegalArgumentException("Method " + method + " has @SubscribeEvent annotation, but takes an "
                    + "argument that is not a subtype of the base type " + baseType + ": " + eventType);
        }
        SubscribeEvent ann = method.getAnnotation(SubscribeEvent.class);
        if (ann == null) {
            // 来自父类声明的注解（子类重写时没写注解）
            ann = findInheritedAnnotation(method);
        }
        EventPriority priority = ann == null ? EventPriority.NORMAL : ann.priority();
        boolean receiveCanceled = ann != null && ann.receiveCanceled();
        Type filter = null;
        if (IGenericEvent.class.isAssignableFrom(eventType)) {
            Type t = method.getGenericParameterTypes()[0];
            if (t instanceof ParameterizedType pt) {
                filter = pt.getActualTypeArguments()[0];
                if (filter instanceof ParameterizedType inner) {
                    filter = inner.getRawType();
                }
            }
        }
        MethodHandle mh;
        try {
            method.setAccessible(true);
            mh = MethodHandles.lookup().unreflect(method);
            if (instance != null) {
                mh = mh.bindTo(instance);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Error registering event handler: {} {}", eventType, method, e);
            return;
        }
        String name = method.getDeclaringClass().getName() + "." + method.getName();
        IEventListener l = new MethodListener(mh, receiveCanceled, filter, name);
        addToListeners(key, eventType, l, priority);
    }

    private static SubscribeEvent findInheritedAnnotation(Method m) {
        Deque<Class<?>> q = new ArrayDeque<>();
        q.add(m.getDeclaringClass());
        Set<Class<?>> seen = new HashSet<>();
        while (!q.isEmpty()) {
            Class<?> c = q.poll();
            if (c == null || !seen.add(c)) {
                continue;
            }
            try {
                Method d = c.getDeclaredMethod(m.getName(), m.getParameterTypes());
                SubscribeEvent a = d.getAnnotation(SubscribeEvent.class);
                if (a != null) {
                    return a;
                }
            } catch (NoSuchMethodException ignored) {
                // 继续找父类
            }
            q.add(c.getSuperclass());
            q.addAll(Arrays.asList(c.getInterfaces()));
        }
        return null;
    }

    private static final class MethodListener implements IEventListener {
        private final MethodHandle handle;
        private final boolean receiveCanceled;
        private final Type filter;
        private final String name;

        MethodListener(MethodHandle handle, boolean receiveCanceled, Type filter, String name) {
            this.handle = handle;
            this.receiveCanceled = receiveCanceled;
            this.filter = filter;
            this.name = name;
        }

        @Override
        public void invoke(Event event) {
            if (!receiveCanceled && event.isCanceled()) {
                return;
            }
            if (filter != null && ((IGenericEvent<?>) event).getGenericType() != filter) {
                return;
            }
            try {
                handle.invoke(event);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        @Override
        public String listenerName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // ---- lambda 监听器 ----

    @Override
    public <T extends Event> void addListener(Consumer<T> consumer) {
        addListener(EventPriority.NORMAL, consumer);
    }

    @Override
    public <T extends Event> void addListener(EventPriority priority, Consumer<T> consumer) {
        addListener(priority, false, consumer);
    }

    @Override
    public <T extends Event> void addListener(EventPriority priority, boolean receiveCancelled, Consumer<T> consumer) {
        Class<T> type = eventClassOf(consumer);
        checkNotGeneric(type);
        addConsumer(priority, receiveCancelled, null, type, consumer);
    }

    @Override
    public <T extends Event> void addListener(EventPriority priority, boolean receiveCancelled, Class<T> eventType,
                                              Consumer<T> consumer) {
        checkNotGeneric(eventType);
        addConsumer(priority, receiveCancelled, null, eventType, consumer);
    }

    @Override
    public <T extends GenericEvent<? extends F>, F> void addGenericListener(Class<F> genericClassFilter,
                                                                            Consumer<T> consumer) {
        addGenericListener(genericClassFilter, EventPriority.NORMAL, consumer);
    }

    @Override
    public <T extends GenericEvent<? extends F>, F> void addGenericListener(Class<F> genericClassFilter,
                                                                            EventPriority priority, Consumer<T> consumer) {
        addGenericListener(genericClassFilter, priority, false, consumer);
    }

    @Override
    public <T extends GenericEvent<? extends F>, F> void addGenericListener(Class<F> genericClassFilter,
                                                                            EventPriority priority,
                                                                            boolean receiveCancelled,
                                                                            Consumer<T> consumer) {
        addConsumer(priority, receiveCancelled, genericClassFilter, eventClassOf(consumer), consumer);
    }

    @Override
    public <T extends GenericEvent<? extends F>, F> void addGenericListener(Class<F> genericClassFilter,
                                                                            EventPriority priority,
                                                                            boolean receiveCancelled,
                                                                            Class<T> eventType, Consumer<T> consumer) {
        addConsumer(priority, receiveCancelled, genericClassFilter, eventType, consumer);
    }

    private static void checkNotGeneric(Class<?> type) {
        if (GenericEvent.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(
                    "Cannot register a generic event listener with addListener, use addGenericListener");
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Event> Class<T> eventClassOf(Consumer<T> consumer) {
        Class<?> c = TypeResolver.resolveRawArgument(Consumer.class, consumer.getClass());
        if (c == TypeResolver.Unknown.class) {
            LOGGER.error("Failed to resolve handler for \"{}\"", consumer);
            throw new IllegalStateException("Failed to resolve consumer event type: " + consumer);
        }
        if (c == Event.class) {
            LOGGER.warn("Attempting to add a Lambda listener with computed generic type of Event. Are you sure this is "
                    + "what you meant? NOTE : there are complex lambda forms where the generic type information is "
                    + "erased and cannot be recovered at runtime.");
        }
        return (Class<T>) c;
    }

    private <T extends Event> void addConsumer(EventPriority priority, boolean receiveCancelled, Class<?> genericFilter,
                                               Class<T> eventType, Consumer<T> consumer) {
        if (baseType != Event.class && !baseType.isAssignableFrom(eventType)) {
            throw new IllegalArgumentException("Listener for event " + eventType
                    + " takes an argument that is not a subtype of the base type " + baseType);
        }
        Predicate<Event> filter = e -> (receiveCancelled || !e.isCanceled())
                && (genericFilter == null || ((IGenericEvent<?>) e).getGenericType() == genericFilter);
        String name = consumer.getClass().getName();
        IEventListener l = new IEventListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void invoke(Event event) {
                if (filter.test(event)) {
                    consumer.accept((T) event);
                }
            }

            @Override
            public String listenerName() {
                return name;
            }

            @Override
            public String toString() {
                return name;
            }
        };
        addToListeners(consumer, eventType, l, priority);
    }

    private void addToListeners(Object key, Class<?> eventType, IEventListener listener, EventPriority priority) {
        EventListenerHelper.getListenerList(eventType).register(busID, priority, listener);
        listeners.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>())).add(listener);
    }

    @Override
    public void unregister(Object object) {
        List<IEventListener> l = listeners.remove(object);
        if (l == null) {
            return;
        }
        for (IEventListener listener : l) {
            ListenerList.unregisterAll(busID, listener);
        }
    }

    // ---- 发布 ----

    @Override
    public boolean post(Event event) {
        return post(event, IEventListener::invoke);
    }

    @Override
    public boolean post(Event event, IEventBusInvokeDispatcher wrapper) {
        if (shutdown) {
            return false;
        }
        if (checkTypesOnDispatch && !baseType.isInstance(event)) {
            throw new IllegalArgumentException("Cannot post event of type " + event.getClass().getSimpleName()
                    + " to this event. Must match type: " + baseType.getSimpleName());
        }
        IEventListener[] ls = event.getListenerList().getListeners(busID);
        int index = 0;
        try {
            for (; index < ls.length; index++) {
                IEventListener l = ls[index];
                if (l instanceof EventPriority p) {
                    if (trackedPhases.contains(p)) {
                        l.invoke(event);
                    }
                    continue;
                }
                wrapper.invoke(l, event);
            }
        } catch (Throwable t) {
            exceptionHandler.handleException(this, event, ls, index, t);
            throw t;
        }
        return event.isCanceled();
    }

    @Override
    public void handleException(IEventBus bus, Event event, IEventListener[] listeners, int index, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append("Exception caught during firing event: ").append(throwable.getMessage()).append('\n');
        sb.append("\tIndex: ").append(index).append('\n');
        sb.append("\tListeners:\n");
        for (int i = 0; i < listeners.length; i++) {
            sb.append("\t\t").append(i).append(": ").append(listeners[i].listenerName()).append('\n');
        }
        LOGGER.error(sb.toString(), throwable);
    }

    @Override
    public void shutdown() {
        LOGGER.fatal("EventBus {} shutting down - future events will not be posted.", busID);
        shutdown = true;
    }

    @Override
    public void start() {
        shutdown = false;
    }
}
