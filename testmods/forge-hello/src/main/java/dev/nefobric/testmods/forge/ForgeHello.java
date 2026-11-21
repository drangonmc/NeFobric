package dev.nefobric.testmods.forge;

import net.minecraftforge.fml.common.Mod;

@Mod("forge_hello")
public class ForgeHello {
    public ForgeHello() {
        ClassLoader cl = ForgeHello.class.getClassLoader();
        System.out.println("[forge_hello] constructed: Hello from Forge! / 你好，Forge！ thread="
                + Thread.currentThread().getName() + ", loader=" + (cl == null ? "bootstrap" : cl.getName()));
    }
}
