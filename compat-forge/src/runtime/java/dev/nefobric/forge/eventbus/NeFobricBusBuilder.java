package dev.nefobric.forge.eventbus;

import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.IEventExceptionHandler;

import java.util.EnumSet;

/** {@link BusBuilder} 的实现。 */
public final class NeFobricBusBuilder implements BusBuilder {
    IEventExceptionHandler exceptionHandler;
    EnumSet<EventPriority> phases = EnumSet.allOf(EventPriority.class);
    boolean startShutdown;
    boolean checkTypesOnDispatch;
    Class<?> markerType = Event.class;

    @Override
    public BusBuilder setTrackPhases(boolean trackPhases) {
        phases = trackPhases ? EnumSet.allOf(EventPriority.class) : EnumSet.noneOf(EventPriority.class);
        return this;
    }

    @Override
    public BusBuilder setPhasesToTrack(EnumSet<EventPriority> phases) {
        this.phases = EnumSet.copyOf(phases);
        return this;
    }

    @Override
    public BusBuilder setExceptionHandler(IEventExceptionHandler handler) {
        this.exceptionHandler = handler;
        return this;
    }

    @Override
    public BusBuilder startShutdown() {
        startShutdown = true;
        return this;
    }

    @Override
    public BusBuilder checkTypesOnDispatch() {
        checkTypesOnDispatch = true;
        return this;
    }

    @Override
    public BusBuilder markerType(Class<?> type) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Cannot specify a class marker type");
        }
        markerType = type;
        return this;
    }

    @Override
    public BusBuilder useModLauncher() {
        return this;
    }

    @Override
    public IEventBus build() {
        return new NeFobricEventBus(this);
    }
}
