package net.minecraftforge.eventbus;

import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.IEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 一个事件类在各条总线上的监听器。子类的列表包含父类的监听器：同一优先级里先执行父类事件的监听器。
 * 取到的数组里在每个优先级前面插一个 {@link EventPriority} 作为阶段标记。
 */
public class ListenerList {
    private static final List<ListenerList> ALL = new CopyOnWriteArrayList<>();
    private static final IEventListener[] EMPTY = new IEventListener[0];

    private final ListenerList parent;
    private final List<ListenerList> children = new CopyOnWriteArrayList<>();
    /** 下标是总线 id，然后是优先级。 */
    private volatile List<List<IEventListener>[]> perBus = new ArrayList<>();
    private volatile IEventListener[][] cache = new IEventListener[0][];

    public ListenerList() {
        this(null);
    }

    public ListenerList(ListenerList parent) {
        this.parent = parent;
        if (parent != null) {
            parent.children.add(this);
        }
        ALL.add(this);
    }

    public IEventListener[] getListeners(int busID) {
        IEventListener[][] c = cache;
        if (busID < c.length && c[busID] != null) {
            return c[busID];
        }
        synchronized (this) {
            IEventListener[] built = build(busID);
            IEventListener[][] next = cache.length > busID ? cache.clone() : java.util.Arrays.copyOf(cache, busID + 1);
            next[busID] = built;
            cache = next;
            return built;
        }
    }

    private IEventListener[] build(int busID) {
        List<IEventListener> out = new ArrayList<>();
        for (EventPriority p : EventPriority.values()) {
            List<IEventListener> l = listeners(busID, p);
            if (!l.isEmpty()) {
                out.add(p);
                out.addAll(l);
            }
        }
        return out.isEmpty() ? EMPTY : out.toArray(EMPTY);
    }

    private List<IEventListener> listeners(int busID, EventPriority p) {
        List<IEventListener> out = new ArrayList<>();
        if (parent != null) {
            out.addAll(parent.listeners(busID, p));
        }
        List<List<IEventListener>[]> b = perBus;
        if (busID < b.size() && b.get(busID) != null) {
            out.addAll(b.get(busID)[p.ordinal()]);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public synchronized void register(int busID, EventPriority priority, IEventListener listener) {
        List<List<IEventListener>[]> b = new ArrayList<>(perBus);
        while (b.size() <= busID) {
            b.add(null);
        }
        if (b.get(busID) == null) {
            List<IEventListener>[] arr = new List[EventPriority.values().length];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = new CopyOnWriteArrayList<>();
            }
            b.set(busID, arr);
        }
        b.get(busID)[priority.ordinal()].add(listener);
        perBus = b;
        invalidate();
    }

    /** 兼容 Forge 签名的重载。 */
    public void register(int busID, IEventBus bus, EventPriority priority, IEventListener listener) {
        register(busID, priority, listener);
    }

    public synchronized void unregister(int busID, IEventListener listener) {
        List<List<IEventListener>[]> b = perBus;
        if (busID < b.size() && b.get(busID) != null) {
            for (List<IEventListener> l : b.get(busID)) {
                l.remove(listener);
            }
        }
        invalidate();
    }

    public static void unregisterAll(int busID, IEventListener listener) {
        for (ListenerList l : ALL) {
            l.unregister(busID, listener);
        }
    }

    private void invalidate() {
        cache = new IEventListener[0][];
        for (ListenerList c : children) {
            c.invalidate();
        }
    }
}
