package net.minecraftforge.fml.common;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.Bindings;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Supplier;

/** 标记 Forge 模组主类；值是 mods.toml 里的 modId。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value();

    /** 加载时自动把类的静态 {@code @SubscribeEvent} 方法注册到 Forge 总线或模组总线。 */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface EventBusSubscriber {
        Dist[] value() default {Dist.CLIENT, Dist.DEDICATED_SERVER};

        String modid() default "";

        Bus bus() default Bus.FORGE;

        enum Bus {
            FORGE(Bindings.getForgeBus()),
            MOD(() -> FMLJavaModLoadingContext.get().getModEventBus());

            private final Supplier<IEventBus> busSupplier;

            Bus(final Supplier<IEventBus> eventBusSupplier) {
                this.busSupplier = eventBusSupplier;
            }

            public Supplier<IEventBus> bus() {
                return busSupplier;
            }
        }
    }
}
