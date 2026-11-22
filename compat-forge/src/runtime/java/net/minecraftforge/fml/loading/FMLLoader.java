package net.minecraftforge.fml.loading;

import cpw.mods.modlauncher.api.INameMappingService;
import dev.nefobric.forge.fml.ForgeEnvironment;
import net.minecraftforge.api.distmarker.Dist;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/** FML 加载器的公开状态。NeFobric 用自己的加载流程，这里只回答模组会问的问题。 */
public class FMLLoader {
    public static Runnable progressWindowTick = () -> {
    };

    public static Dist getDist() {
        return ForgeEnvironment.dist();
    }

    public static LoadingModList getLoadingModList() {
        return LoadingModList.get();
    }

    public static Path getGamePath() {
        return ForgeEnvironment.gameDir();
    }

    public static String getNaming() {
        return "srg";
    }

    public static Optional<BiFunction<INameMappingService.Domain, String, String>> getNameFunction(String naming) {
        return Optional.empty();
    }

    public static String getLauncherInfo() {
        return "NeFobric";
    }

    public static List<Map<String, String>> modLauncherModList() {
        return List.of();
    }

    public static String launcherHandlerName() {
        return getDist().isClient() ? "forgeclient" : "forgeserver";
    }

    public static boolean isProduction() {
        return true;
    }

    public static boolean isSecureJarEnabled() {
        return false;
    }

    /** 1.20.1 的 Forge 把游戏和模组放在一个 ModuleLayer 里；NeFobric 用平铺类加载器，所在的层是启动层。 */
    public static ModuleLayer getGameLayer() {
        return ModuleLayer.boot();
    }

    public static VersionInfo versionInfo() {
        return ForgeEnvironment.versionInfo();
    }
}
