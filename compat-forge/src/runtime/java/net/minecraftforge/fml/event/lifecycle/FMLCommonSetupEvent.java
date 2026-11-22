package net.minecraftforge.fml.event.lifecycle;

import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingStage;

public class FMLCommonSetupEvent extends ParallelDispatchEvent {
    public FMLCommonSetupEvent(final ModContainer container, final ModLoadingStage stage) {
        super(container, stage);
    }
}
