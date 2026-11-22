package net.minecraftforge.fml.event.lifecycle;

import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingStage;

public class FMLClientSetupEvent extends ParallelDispatchEvent {
    public FMLClientSetupEvent(final ModContainer container, final ModLoadingStage stage) {
        super(container, stage);
    }
}
