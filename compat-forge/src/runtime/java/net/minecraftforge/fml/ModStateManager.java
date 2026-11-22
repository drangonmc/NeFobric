package net.minecraftforge.fml;

import dev.nefobric.forge.fml.NeFobricStates;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收集所有加载状态，并在每个阶段里按 {@code previous} 排成拓扑序（同层按注册顺序）。Forge 用 ServiceLoader 找
 * 状态提供者；NeFobric 的提供者登记在 {@link NeFobricStates}。
 */
public class ModStateManager {
    static ModStateManager INSTANCE;
    private final EnumMap<ModLoadingPhase, List<IModLoadingState>> stateMap = new EnumMap<>(ModLoadingPhase.class);

    public ModStateManager() {
        INSTANCE = this;
        for (IModStateProvider provider : NeFobricStates.providers()) {
            for (IModLoadingState state : provider.getAllStates()) {
                stateMap.computeIfAbsent(state.phase(), p -> new ArrayList<>()).add(state);
            }
        }
    }

    public List<IModLoadingState> getStates(final ModLoadingPhase phase) {
        List<IModLoadingState> nodes = stateMap.getOrDefault(phase, List.of());
        Map<String, IModLoadingState> byName = new HashMap<>();
        nodes.forEach(n -> byName.put(n.name(), n));
        Map<IModLoadingState, List<IModLoadingState>> children = new HashMap<>();
        List<IModLoadingState> roots = new ArrayList<>();
        for (IModLoadingState n : nodes) {
            IModLoadingState parent = byName.get(n.previous());
            if (parent == null || parent == n) {
                roots.add(n);
            } else {
                children.computeIfAbsent(parent, k -> new ArrayList<>()).add(n);
            }
        }
        // 广度优先，同层按注册顺序：和 Forge 的拓扑排序（按 indexOf 打破平局）结果一致
        List<IModLoadingState> out = new ArrayList<>();
        List<IModLoadingState> frontier = new ArrayList<>(roots);
        while (!frontier.isEmpty()) {
            frontier.sort((a, b) -> Integer.compare(nodes.indexOf(a), nodes.indexOf(b)));
            IModLoadingState next = frontier.remove(0);
            out.add(next);
            frontier.addAll(children.getOrDefault(next, List.of()));
        }
        return out;
    }

    public IModLoadingState findState(final String stateName) {
        return stateMap.values().stream()
                .flatMap(Collection::stream)
                .filter(mls -> mls.name().equals(stateName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown IModLoadingState: " + stateName));
    }
}
