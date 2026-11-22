package dev.nefobric.testmods.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** 给 ApiProbe 检查 @Environment 剥离用：另一端的方法应该不存在。 */
public final class EnvProbe {
    private EnvProbe() {
    }

    @Environment(EnvType.CLIENT)
    public static String clientOnly() {
        return "client";
    }

    @Environment(EnvType.SERVER)
    public static String serverOnly() {
        return "server";
    }
}
