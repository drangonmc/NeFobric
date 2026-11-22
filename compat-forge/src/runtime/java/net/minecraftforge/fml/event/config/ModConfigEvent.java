package net.minecraftforge.fml.event.config;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.config.IConfigEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.IModBusEvent;

/** 配置文件加载、重新加载（文件被改动或服务器同步）、卸载。在所属模组的模组总线上分发。 */
public class ModConfigEvent extends Event implements IModBusEvent, IConfigEvent {
    private final ModConfig config;

    ModConfigEvent(final ModConfig config) {
        this.config = config;
    }

    @Override
    public ModConfig getConfig() {
        return config;
    }

    public static class Loading extends ModConfigEvent {
        public Loading(final ModConfig config) {
            super(config);
        }
    }

    public static class Reloading extends ModConfigEvent {
        public Reloading(final ModConfig config) {
            super(config);
        }
    }

    public static class Unloading extends ModConfigEvent {
        public Unloading(final ModConfig config) {
            super(config);
        }
    }
}
