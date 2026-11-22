package dev.nefobric.forge.fml;

import net.minecraftforge.fml.IModStateProvider;
import net.minecraftforge.fml.core.ModStateProvider;

import java.util.ArrayList;
import java.util.List;

/** 加载状态提供者（Forge 用 ServiceLoader 找）。FML 的在最前面，Forge 本体（注册表、冻结……）的之后加入。 */
public final class NeFobricStates {
    private static final List<IModStateProvider> PROVIDERS = new ArrayList<>(List.of(new ModStateProvider()));

    private NeFobricStates() {
    }

    public static synchronized void register(IModStateProvider provider) {
        PROVIDERS.add(provider);
    }

    public static synchronized List<IModStateProvider> providers() {
        return List.copyOf(PROVIDERS);
    }
}
