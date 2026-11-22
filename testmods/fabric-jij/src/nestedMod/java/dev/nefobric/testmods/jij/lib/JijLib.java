package dev.nefobric.testmods.jij.lib;

import net.fabricmc.api.ModInitializer;

public class JijLib implements ModInitializer {
    public static String greet() {
        return "hello from a nested mod";
    }

    @Override
    public void onInitialize() {
        System.out.println("[fabric_jij_lib] nested mod entrypoint ran");
    }
}
