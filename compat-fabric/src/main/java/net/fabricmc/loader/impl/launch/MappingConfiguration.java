package net.fabricmc.loader.impl.launch;

import dev.nefobric.compat.fabric.impl.CoreBridge;
import dev.nefobric.loader.impl.NeFobricLoader;

/** Fabric Loader 内部类的兼容实现：只提供游戏信息和运行时命名空间，不提供映射树本身。 */
public final class MappingConfiguration {
    public String getGameId() {
        return "minecraft";
    }

    public String getGameVersion() {
        return NeFobricLoader.isInitialized() ? NeFobricLoader.get().game().version() : null;
    }

    public boolean matches(String gameId, String gameVersion) {
        return getGameId().equals(gameId) && (gameVersion == null || gameVersion.equals(getGameVersion()));
    }

    public String getTargetNamespace() {
        return CoreBridge.runtimeNamespace();
    }

    public boolean requiresPackageAccessHack() {
        return false;
    }
}
