package dev.nefobric.testmods.forge;

import net.minecraftforge.common.capabilities.AutoRegisterCapability;

/** forge-hello 的能力接口：带 {@code @AutoRegisterCapability}，加载时自动注册。 */
@AutoRegisterCapability
public interface HelloCapability {
    String greet();
}
