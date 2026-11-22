package net.minecraftforge.eventbus.api;

import dev.nefobric.forge.eventbus.NeFobricBusBuilder;

import java.util.EnumSet;

/** 创建事件总线。 */
public interface BusBuilder {
    static BusBuilder builder() {
        return new NeFobricBusBuilder();
    }

    BusBuilder setTrackPhases(boolean trackPhases);

    default BusBuilder setPhasesToTrack(EnumSet<EventPriority> phases) {
        throw new UnsupportedOperationException();
    }

    default BusBuilder setPhasesToTrack(EventPriority... phases) {
        return setPhasesToTrack(EnumSet.of(phases[0], phases));
    }

    default BusBuilder setPhasesToTrack(EventPriority phase) {
        return setPhasesToTrack(EnumSet.of(phase));
    }

    BusBuilder setExceptionHandler(IEventExceptionHandler handler);

    BusBuilder startShutdown();

    BusBuilder checkTypesOnDispatch();

    BusBuilder markerType(Class<?> type);

    /** 在 Forge 里表示“用 ModLauncher 的类加载器生成监听器类”。NeFobric 不需要，保留以兼容。 */
    BusBuilder useModLauncher();

    IEventBus build();
}
