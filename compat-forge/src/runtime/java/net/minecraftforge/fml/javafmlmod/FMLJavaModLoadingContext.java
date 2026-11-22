package net.minecraftforge.fml.javafmlmod;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

/** 传给 {@code @Mod} 主类构造函数的上下文；{@link #getModEventBus()} 是这个模组自己的模组总线。 */
public class FMLJavaModLoadingContext extends ModLoadingContext {
    private final FMLModContainer container;

    FMLJavaModLoadingContext(FMLModContainer container) {
        this.container = container;
    }

    public IEventBus getModEventBus() {
        return container.getEventBus();
    }

    @Override
    public FMLModContainer getContainer() {
        return container;
    }

    @Deprecated(forRemoval = true, since = "1.21.1")
    public static FMLJavaModLoadingContext get() {
        return ModLoadingContext.get().extension();
    }
}
