package dev.nefobric.testmods.neoforge;

import net.minecraftforge.fml.common.Mod;

@Mod("neoforge_hello")
public class NeoForgeHello {
    public NeoForgeHello() {
        ClassLoader cl = NeoForgeHello.class.getClassLoader();
        System.out.println("[neoforge_hello] constructed: Hello from NeoForge! / 你好，NeoForge！ thread="
                + Thread.currentThread().getName() + ", loader=" + (cl == null ? "bootstrap" : cl.getName()));
    }
}
