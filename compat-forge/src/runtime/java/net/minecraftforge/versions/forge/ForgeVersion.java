package net.minecraftforge.versions.forge;

import dev.nefobric.forge.fml.ForgeEnvironment;
import net.minecraftforge.fml.VersionChecker;
import org.jetbrains.annotations.Nullable;

/** Forge 版本号。NeFobric 声明自己兼容的 Forge 47.x 版本（见 ForgeAdapter.EMULATED_FORGE_VERSION）。 */
public class ForgeVersion {
    public static final String MOD_ID = "forge";

    public static String getVersion() {
        return ForgeEnvironment.forgeVersion();
    }

    public static VersionChecker.Status getStatus() {
        return VersionChecker.Status.PENDING;
    }

    @Nullable
    public static String getTarget() {
        return null;
    }

    public static String getSpec() {
        return ForgeEnvironment.FORGE_SPEC;
    }

    public static String getGroup() {
        return ForgeEnvironment.FORGE_GROUP;
    }
}
