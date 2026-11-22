package net.minecraftforge.common;

import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.versions.forge.ForgeVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

/**
 * Forge 总线：游戏事件（tick、玩家、世界、渲染……）都在这里分发。和 Forge 一样以“暂停”状态创建，
 * 模组加载完成后才 {@code start()}，在那之前 post 的事件不分发。
 */
public class MinecraftForge {
    public static final IEventBus EVENT_BUS = BusBuilder.builder().startShutdown().useModLauncher().build();
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker FORGE = MarkerManager.getMarker("FORGE");

    public static void initialize() {
        LOGGER.info(FORGE, "MinecraftForge v{} Initialized (NeFobric)", ForgeVersion.getVersion());
    }
}
