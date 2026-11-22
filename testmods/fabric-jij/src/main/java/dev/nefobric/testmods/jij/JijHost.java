package dev.nefobric.testmods.jij;

import dev.nefobric.testmods.jij.lib.JijLib;
import dev.nefobric.testmods.jij.plain.PlainLib;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class JijHost implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        System.out.println("[fabric_jij] host: " + JijLib.greet()
                + ", plain=" + PlainLib.value()
                + ", libLoaded=" + loader.isModLoaded("fabric_jij_lib")
                + ", deepLoaded=" + loader.isModLoaded("fabric_jij_deep"));
    }
}
