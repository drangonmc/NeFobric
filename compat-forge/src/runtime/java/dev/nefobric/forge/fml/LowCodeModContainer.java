package dev.nefobric.forge.fml;

import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.forgespi.language.IModInfo;

/** 没有 {@code @Mod} 主类的模组（lowcodefml，只有资源/数据包）。 */
public final class LowCodeModContainer extends ModContainer {
    private final Object marker = new Object();
    private final IEventBus eventBus = BusBuilder.builder().markerType(IModBusEvent.class).build();

    public LowCodeModContainer(IModInfo info) {
        super(info);
        this.contextExtension = () -> null;
    }

    @Override
    public boolean matches(Object mod) {
        return mod == marker;
    }

    @Override
    public Object getMod() {
        return marker;
    }

    @Override
    protected <T extends Event & IModBusEvent> void acceptEvent(T e) {
        eventBus.post(e);
    }
}
