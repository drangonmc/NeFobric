package dev.nefobric.forge.fml;

import net.minecraftforge.common.ForgeConfig;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.logging.log4j.LogManager;

import java.util.Optional;

/**
 * minecraft 和 forge 两个虚拟模组的容器。forge 的模组总线给 NeFobric 自己的 Forge 实现用（例如在 RegisterEvent
 * 里注册 Forge 自带的内容），和 Forge 里 ForgeMod 用自己的模组总线一样。
 */
public final class BuiltinModContainer extends ModContainer {
    private final Object marker = new Object() {
        @Override
        public String toString() {
            return "NeFobric builtin mod " + modId;
        }
    };
    private final IEventBus eventBus;

    public BuiltinModContainer(IModInfo info) {
        super(info);
        this.eventBus = BusBuilder.builder()
                .setExceptionHandler((bus, event, listeners, index, t) ->
                        LogManager.getLogger().error("Exception in builtin mod {} handling {}", modId, event, t))
                .markerType(IModBusEvent.class).build();
        this.contextExtension = () -> null;
        this.configHandler = Optional.of(ce -> eventBus.post(ce.self()));
        if ("forge".equals(info.getModId())) {
            // 和 ForgeMod 的构造函数一样：注册 Forge 自己的三份配置
            addConfig(new ModConfig(ModConfig.Type.CLIENT, ForgeConfig.clientSpec(), this));
            addConfig(new ModConfig(ModConfig.Type.SERVER, ForgeConfig.serverSpec(), this));
            addConfig(new ModConfig(ModConfig.Type.COMMON, ForgeConfig.commonSpec(), this));
            eventBus.register(ForgeConfig.class);
        }
    }

    public IEventBus getEventBus() {
        return eventBus;
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
