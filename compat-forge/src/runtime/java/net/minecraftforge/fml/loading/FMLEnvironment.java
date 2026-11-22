package net.minecraftforge.fml.loading;

import net.minecraftforge.api.distmarker.Dist;

/** 当前运行环境。NeFobric 上始终是生产环境、SRG 命名。 */
public class FMLEnvironment {
    public static final Dist dist = FMLLoader.getDist();
    public static final String naming = FMLLoader.getNaming();
    public static final boolean production = FMLLoader.isProduction() || System.getProperties().containsKey("production");
    public static final boolean secureJarsEnabled = FMLLoader.isSecureJarEnabled();
}
