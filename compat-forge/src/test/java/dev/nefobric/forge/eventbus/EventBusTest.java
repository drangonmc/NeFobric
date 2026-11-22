package dev.nefobric.forge.eventbus;

import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.GenericEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.IModBusEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 事件总线的公开语义，按 Forge EventBus 6 的行为写。 */
class EventBusTest {
    public static class Base extends Event {
    }

    public static class Child extends Base {
    }

    @Cancelable
    public static class Stoppable extends Event {
    }

    public static class Typed<T> extends GenericEvent<T> {
        public Typed(Class<T> type) {
            super(type);
        }
    }

    public static class ModEvent extends Event implements IModBusEvent {
    }

    static final List<String> LOG = new ArrayList<>();

    public static class StaticListeners {
        @SubscribeEvent
        public static void onBase(Base e) {
            LOG.add("static base");
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onChild(Child e) {
            LOG.add("static child high");
        }
    }

    public static class InstanceListeners {
        final String name;

        InstanceListeners(String name) {
            this.name = name;
        }

        @SubscribeEvent
        public void onBase(Base e) {
            LOG.add(name + " base");
        }

        @SubscribeEvent
        void packagePrivate(Child e) {
            LOG.add(name + " child");
        }
    }

    @Test
    void priorityAndHierarchy() {
        LOG.clear();
        IEventBus bus = BusBuilder.builder().build();
        bus.register(StaticListeners.class);
        bus.addListener(EventPriority.LOWEST, (Child e) -> LOG.add("lambda lowest"));
        bus.addListener((Base e) -> LOG.add("lambda base"));
        bus.post(new Child());
        // HIGH 先；NORMAL 里父类事件的监听器在前；最后 LOWEST
        assertEquals(List.of("static child high", "static base", "lambda base", "lambda lowest"), LOG);
    }

    @Test
    void instanceRegistrationAndUnregister() {
        LOG.clear();
        IEventBus bus = BusBuilder.builder().build();
        InstanceListeners a = new InstanceListeners("a");
        bus.register(a);
        bus.post(new Child());
        assertEquals(List.of("a base", "a child"), LOG);
        bus.unregister(a);
        LOG.clear();
        bus.post(new Child());
        assertTrue(LOG.isEmpty());
    }

    @Test
    void cancellation() {
        LOG.clear();
        IEventBus bus = BusBuilder.builder().build();
        bus.addListener(EventPriority.HIGH, (Stoppable e) -> {
            LOG.add("high");
            e.setCanceled(true);
        });
        bus.addListener((Stoppable e) -> LOG.add("normal"));
        bus.addListener(EventPriority.LOW, true, (Stoppable e) -> LOG.add("low receives canceled"));
        assertTrue(bus.post(new Stoppable()));
        assertEquals(List.of("high", "low receives canceled"), LOG);
        assertThrows(UnsupportedOperationException.class, () -> new Base().setCanceled(true));
    }

    @Test
    void genericFilter() {
        LOG.clear();
        IEventBus bus = BusBuilder.builder().build();
        bus.addGenericListener(String.class, (Typed<String> e) -> LOG.add("string"));
        bus.addGenericListener(Integer.class, (Typed<Integer> e) -> LOG.add("integer"));
        bus.post(new Typed<>(String.class));
        assertEquals(List.of("string"), LOG);
    }

    @Test
    void markerTypeRejectsOtherEvents() {
        IEventBus modBus = BusBuilder.builder().markerType(IModBusEvent.class).build();
        modBus.addListener((ModEvent e) -> LOG.add("mod"));
        assertThrows(IllegalArgumentException.class, () -> modBus.addListener((Base e) -> LOG.add("x")));
    }

    @Test
    void startShutdownDropsEventsUntilStarted() {
        LOG.clear();
        IEventBus bus = BusBuilder.builder().startShutdown().build();
        bus.addListener((Base e) -> LOG.add("got"));
        assertFalse(bus.post(new Base()));
        assertTrue(LOG.isEmpty());
        bus.start();
        bus.post(new Base());
        assertEquals(List.of("got"), LOG);
    }
}
