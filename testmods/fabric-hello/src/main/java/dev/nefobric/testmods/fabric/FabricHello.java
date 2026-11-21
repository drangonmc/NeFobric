package dev.nefobric.testmods.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class FabricHello implements PreLaunchEntrypoint, ModInitializer, ClientModInitializer, DedicatedServerModInitializer {

    private static void log(String message) {
        System.out.println("[fabric_hello] " + message);
    }

    @Override
    public void onPreLaunch() {
        log("preLaunch");
    }

    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        log("main: Hello from Fabric! / 你好，Fabric！"
                + " thread=" + Thread.currentThread().getName()
                + ", env=" + loader.getEnvironmentType()
                + ", forge_hello=" + loader.isModLoaded("forge_hello")
                + ", neoforge_hello=" + loader.isModLoaded("neoforge_hello")
                + ", minecraft=" + loader.isModLoaded("minecraft")
                + ", gameLoader=" + gameClassLoader(loader.getEnvironmentType()));
    }

    @Override
    public void onInitializeClient() {
        log("client: client entrypoint ran");
    }

    @Override
    public void onInitializeServer() {
        log("server: server entrypoint ran");
    }

    /** 游戏主类和本模组应该由同一个类加载器提供。 */
    private String gameClassLoader(EnvType env) {
        String main = env == EnvType.CLIENT ? "net.minecraft.client.main.Main" : "net.minecraft.server.Main";
        try {
            ClassLoader cl = Class.forName(main, false, getClass().getClassLoader()).getClassLoader();
            return cl == null ? "bootstrap" : String.valueOf(cl.getName());
        } catch (ClassNotFoundException e) {
            return "missing";
        }
    }
}
