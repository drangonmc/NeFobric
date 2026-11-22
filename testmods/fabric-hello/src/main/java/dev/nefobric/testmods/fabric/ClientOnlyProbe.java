package dev.nefobric.testmods.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** 整个类只属于客户端：在服务端加载它应该失败，和 Fabric Loader 一样。 */
@Environment(EnvType.CLIENT)
public final class ClientOnlyProbe {
    private ClientOnlyProbe() {
    }
}
