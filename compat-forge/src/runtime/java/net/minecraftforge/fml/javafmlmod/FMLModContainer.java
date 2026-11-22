package net.minecraftforge.fml.javafmlmod;

import dev.nefobric.forge.fml.ForgeEnvironment;
import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.IEventListener;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Optional;

/** {@code javafml} 模组的容器：持有 {@code @Mod} 主类、模组总线和构造时传给主类的上下文。 */
public class FMLModContainer extends ModContainer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker LOADING = MarkerManager.getMarker("LOADING");

    private final ModFileScanData scanResults;
    private final IEventBus eventBus;
    private Object modInstance;
    private final Class<?> modClass;
    private final FMLJavaModLoadingContext context = new FMLJavaModLoadingContext(this);

    public FMLModContainer(IModInfo info, String className, ModFileScanData modFileScanResults, ModuleLayer gameLayer) {
        super(info);
        LOGGER.debug(LOADING, "Creating FMLModContainer instance for {}", className);
        this.scanResults = modFileScanResults;
        activityMap.put(ModLoadingStage.CONSTRUCT, this::constructMod);
        this.eventBus = BusBuilder.builder().setExceptionHandler(this::onEventFailed).setTrackPhases(false)
                .markerType(IModBusEvent.class).useModLauncher().build();
        this.configHandler = Optional.of(ce -> this.eventBus.post(ce.self()));
        this.contextExtension = () -> context;
        try {
            // 和 Forge 一样只加载不初始化：静态初始化块在 CONSTRUCT 阶段构造主类时才运行
            modClass = Class.forName(className, false, ForgeEnvironment.gameClassLoader());
            LOGGER.trace(LOADING, "Loaded modclass {} with {}", modClass.getName(), modClass.getClassLoader());
        } catch (Throwable e) {
            LOGGER.error(LOADING, "Failed to load class {}", className, e);
            throw new ModLoadingException(info, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmodclass", e);
        }
    }

    private void onEventFailed(IEventBus bus, Event event, IEventListener[] listeners, int index, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append("Exception caught during firing event: ").append(throwable.getMessage()).append('\n');
        sb.append("\tIndex: ").append(index).append('\n');
        sb.append("\tListeners:\n");
        for (int i = 0; i < listeners.length; i++) {
            sb.append("\t\t").append(i).append(": ").append(listeners[i]).append('\n');
        }
        LOGGER.error(sb.toString(), throwable);
    }

    private void constructMod() {
        try {
            LOGGER.trace(LOADING, "Loading mod instance {} of type {}", getModId(), modClass.getName());
            Constructor<?> constructor;
            try {
                constructor = modClass.getDeclaredConstructor(context.getClass());
            } catch (NoSuchMethodException | SecurityException exception) {
                constructor = modClass.getDeclaredConstructor();
            }
            constructor.setAccessible(true);
            this.modInstance = constructor.getParameterCount() == 0 ? constructor.newInstance()
                    : constructor.newInstance(context);
            LOGGER.trace(LOADING, "Loaded mod instance {} of type {}", getModId(), modClass.getName());
        } catch (Throwable e) {
            if (e instanceof InvocationTargetException wrapped) {
                e = Objects.requireNonNullElse(wrapped.getCause(), e);
            }
            if (e instanceof ExceptionInInitializerError eiie && eiie.getCause() != null) {
                e = eiie.getCause();
            }
            LOGGER.error(LOADING, "Failed to create mod instance. ModID: {}, class {}", getModId(), modClass.getName(), e);
            throw new ModLoadingException(modInfo, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmod", e,
                    modClass);
        }
        try {
            LOGGER.trace(LOADING, "Injecting Automatic event subscribers for {}", getModId());
            AutomaticEventSubscriber.inject(this, this.scanResults, this.modClass.getClassLoader());
        } catch (Throwable e) {
            LOGGER.error(LOADING, "Failed to register automatic subscribers. ModID: {}, class {}", getModId(),
                    modClass.getName(), e);
            throw new ModLoadingException(modInfo, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmod", e,
                    modClass);
        }
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
        return this.eventBus;
    }

    @Override
    protected <T extends Event & IModBusEvent> void acceptEvent(final T e) {
        try {
            LOGGER.trace(LOADING, "Firing event for modid {} : {}", this.getModId(), e);
            this.eventBus.post(e);
        } catch (Throwable t) {
            LOGGER.error(LOADING, "Caught exception during event {} dispatch for modid {}", e, this.getModId(), t);
            throw new ModLoadingException(modInfo, modLoadingStage, "fml.modloading.errorduringevent", t);
        }
    }
}
