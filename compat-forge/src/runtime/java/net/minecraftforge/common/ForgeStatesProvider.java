package net.minecraftforge.common;

import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fml.IModLoadingState;
import net.minecraftforge.fml.IModStateProvider;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingPhase;
import net.minecraftforge.fml.ModLoadingState;

import java.util.List;

/**
 * Forge 本体插进 FML 状态机的加载状态。目前只有 INJECT_CAPABILITIES（构造之后注册能力）；
 * 注册表相关的 CREATE_REGISTRIES、OBJECT_HOLDERS、UNFREEZE_DATA、LOAD_REGISTRIES、FREEZE_DATA、NETWORK_LOCK
 * 和注册表实现一起加入。
 */
public class ForgeStatesProvider implements IModStateProvider {
    final ModLoadingState injectCapabilities = ModLoadingState.withInline("INJECT_CAPABILITIES", "CONSTRUCT",
            ModLoadingPhase.GATHER, ml -> CapabilityManager.INSTANCE.injectCapabilities(ModList.get().getAllScanData()));

    @Override
    public List<IModLoadingState> getAllStates() {
        return List.of(injectCapabilities);
    }
}
