package net.minecraftforge.fml.lowcodemod;

import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 没有 {@code @Mod} 主类的模组（lowcodefml：只有资源包、数据包）。和 Forge 一样有自己的模组总线，
 * 别的模组可以拿它的容器往上挂监听器。
 */
public class LowCodeModContainer extends ModContainer {
    private static final Logger LOGGER = LogManager.getLogger();
    private final ModFileScanData scanResults;
    private final IEventBus eventBus;
    private final Object modInstance = new Object();

    public LowCodeModContainer(IModInfo info, ModFileScanData modFileScanResults, ModuleLayer gameLayer) {
        super(info);
        this.scanResults = modFileScanResults;
        this.contextExtension = () -> null;
        this.eventBus = BusBuilder.builder()
                .setExceptionHandler((bus, event, listeners, index, t) ->
                        LOGGER.error("Exception caught during firing event: {}", event, t))
                .markerType(IModBusEvent.class).build();
    }

    @Override
    public boolean matches(Object mod) {
        return mod == modInstance;
    }

    @Override
    public Object getMod() {
        return modInstance;
    }

    public IEventBus getEventBus() {
        return eventBus;
    }

    @Override
    protected <T extends Event & IModBusEvent> void acceptEvent(T e) {
        try {
            eventBus.post(e);
        } catch (Throwable t) {
            LOGGER.error("Caught exception during event {} dispatch for modid {}", e, getModId(), t);
            throw new ModLoadingException(modInfo, modLoadingStage, "fml.modloading.errorduringevent", t);
        }
    }
}
