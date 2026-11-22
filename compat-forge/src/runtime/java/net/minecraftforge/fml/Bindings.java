package net.minecraftforge.fml;

import dev.nefobric.forge.fml.NeFobricBindings;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.config.IConfigEvent;

import java.util.function.Supplier;

/** FML 核心和 Forge 本体之间的连接点。Forge 用 ServiceLoader 找实现，NeFobric 直接用自己的。 */
public class Bindings {
    private static final IBindingsProvider PROVIDER = new NeFobricBindings();

    public static Supplier<IEventBus> getForgeBus() {
        return PROVIDER.getForgeBusSupplier();
    }

    public static Supplier<I18NParser> getMessageParser() {
        return PROVIDER.getMessageParser();
    }

    public static Supplier<IConfigEvent.ConfigConfig> getConfigConfiguration() {
        return PROVIDER.getConfigConfiguration();
    }
}
