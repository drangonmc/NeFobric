package dev.nefobric.loader.resolve;

import dev.nefobric.loader.Env;
import dev.nefobric.loader.metadata.Dependency;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.util.Messages;
import dev.nefobric.loader.version.MavenVersion;
import dev.nefobric.loader.version.SemanticVersion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 跨生态的依赖解析：按端过滤、处理重复 id、检查依赖、排出加载顺序。
 *
 * <p>规则（与架构文档一致）：
 * <ul>
 *   <li>同一个 id 同时有 Fabric 版和 Forge 系版本时，只加载 Fabric 版并在日志里说明；</li>
 *   <li>同一生态里出现两个相同 id 时报错，让玩家删掉多余的文件；</li>
 *   <li>跨生态只保证“依赖先于被依赖者”，其余按 id 字母顺序，保证每次启动顺序一致。</li>
 * </ul>
 */
public final class ModResolver {
    /** 加载器自带、同时又常被模组单独打包或内嵌的库模组。 */
    private static final Set<String> BUNDLED_LIBRARY_MODS = Set.of("mixinextras");
    /** 去掉没选中的副本带进来的内嵌模组之后重新挑选的次数上限；正常的嵌套两三轮就不再变化。 */
    private static final int MAX_SELECTION_ROUNDS = 16;

    public static final class Result {
        private final List<ModCandidate> mods;
        private final Map<String, ModCandidate> byId;
        private final List<String> errors;
        private final List<String> warnings;
        private final List<String> notes;

        Result(List<ModCandidate> mods, Map<String, ModCandidate> byId, List<String> errors,
               List<String> warnings, List<String> notes) {
            this.mods = Collections.unmodifiableList(mods);
            this.byId = Collections.unmodifiableMap(byId);
            this.errors = Collections.unmodifiableList(errors);
            this.warnings = Collections.unmodifiableList(warnings);
            this.notes = Collections.unmodifiableList(notes);
        }

        /** 选中的模组（不含内置模组），已按加载顺序排列。 */
        public List<ModCandidate> mods() {
            return mods;
        }

        /** 所有可用的 id（含内置模组和 provides 别名）。 */
        public Map<String, ModCandidate> byId() {
            return byId;
        }

        public List<String> errors() {
            return errors;
        }

        public List<String> warnings() {
            return warnings;
        }

        public List<String> notes() {
            return notes;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    private final Env env;

    public ModResolver(Env env) {
        this.env = env;
    }

    public Result resolve(List<ModCandidate> discovered, List<ModCandidate> builtins) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Map<String, ModCandidate> byId = new LinkedHashMap<>();
        for (ModCandidate b : builtins) {
            byId.put(b.id(), b);
            for (String alias : b.provides()) {
                byId.putIfAbsent(alias, b);
            }
        }

        // 1. 按端过滤；内嵌在被跳过的模组里的模组跟着一起跳过
        List<ModCandidate> active = new ArrayList<>();
        for (ModCandidate c : discovered) {
            ModCandidate skippedAncestor = null;
            for (ModCandidate p = c.parent(); p != null && skippedAncestor == null; p = p.parent()) {
                if (!p.environment().appliesTo(env)) {
                    skippedAncestor = p;
                }
            }
            if (!c.environment().appliesTo(env)) {
                notes.add(Messages.tr("resolve.envSkipped", c.describe(), c.environment().displayName()));
            } else if (skippedAncestor != null) {
                notes.add(Messages.tr("resolve.nestedEnvSkipped", c.describe(), skippedAncestor.describe(),
                        skippedAncestor.environment().displayName()));
            } else {
                active.add(c);
            }
        }

        // 2. 处理重复 id。内嵌的模组只有在内嵌它的模组被选中时才能选：没选中的副本带进来的内嵌模组要去掉，
        //    同一个 id 可能因此改选另一个副本，所以重复到结果不再变化
        Selection selection = null;
        List<ModCandidate> candidates = active;
        for (int round = 0; round < MAX_SELECTION_ROUNDS; round++) {
            selection = select(candidates, byId);
            Set<ModCandidate> chosen = identitySet(selection.selected);
            List<ModCandidate> next = active.stream()
                    .filter(c -> c.parent() == null || chosen.contains(c.parent())).toList();
            if (next.equals(candidates)) {
                break;
            }
            candidates = next;
        }
        // 正常情况下上面已经收敛；万一没收敛，至少保证不加载祖先没被选中的内嵌模组
        Set<ModCandidate> chosen = identitySet(selection.selected);
        List<ModCandidate> selected = new ArrayList<>();
        for (ModCandidate c : selection.selected) {
            if (ancestorsChosen(c, chosen)) {
                selected.add(c);
            }
        }
        errors.addAll(selection.errors);
        warnings.addAll(selection.warnings);
        notes.addAll(selection.notes);
        for (ModCandidate c : selected) {
            byId.put(c.id(), c);
        }
        for (ModCandidate c : selected) {
            for (String alias : c.provides()) {
                ModCandidate existing = byId.get(alias);
                if (existing != null && existing != c) {
                    errors.add(Messages.tr("resolve.provideConflict", c.describe(), alias, existing.describe()));
                } else {
                    byId.put(alias, c);
                }
            }
        }

        // 3. 检查依赖
        for (ModCandidate c : selected) {
            for (Dependency d : c.dependencies()) {
                if (!d.side().appliesTo(env)) {
                    continue;
                }
                checkDependency(c, d, byId.get(d.modId()), errors, warnings);
            }
        }

        // 4. 排出加载顺序
        List<ModCandidate> ordered = order(selected, byId, warnings);
        return new Result(ordered, byId, errors, warnings, notes);
    }

    private record Selection(List<ModCandidate> selected, List<String> errors, List<String> warnings,
                             List<String> notes) {
    }

    /** 每个 id 留一个候选。{@code reserved} 里只有内置模组。 */
    private static Selection select(List<ModCandidate> candidates, Map<String, ModCandidate> reserved) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Map<String, List<ModCandidate>> groups = new LinkedHashMap<>();
        for (ModCandidate c : candidates) {
            groups.computeIfAbsent(c.id(), k -> new ArrayList<>()).add(c);
        }
        List<ModCandidate> selected = new ArrayList<>();
        for (Map.Entry<String, List<ModCandidate>> e : groups.entrySet()) {
            String id = e.getKey();
            List<ModCandidate> list = e.getValue();
            if (reserved.containsKey(id)) {
                // 内嵌的副本（以及模组单独打包的 MixinExtras）让给加载器自带的那份；直接放进 mods 的同名模组仍然报错
                List<ModCandidate> topLevel = list.stream()
                        .filter(c -> !c.isNested() && !BUNDLED_LIBRARY_MODS.contains(id)).toList();
                if (topLevel.isEmpty()) {
                    notes.add(Messages.tr("resolve.builtinProvided", id,
                            list.stream().map(ModCandidate::fileName).distinct().collect(Collectors.joining(", "))));
                } else {
                    errors.add(Messages.tr("resolve.reservedId", topLevel.get(0).describe(), id));
                }
                continue;
            }
            if (list.size() == 1) {
                selected.add(list.get(0));
                continue;
            }
            if (list.stream().anyMatch(ModCandidate::isNested)) {
                List<ModCandidate> topLevel = list.stream().filter(c -> !c.isNested()).toList();
                if (topLevel.size() <= 1) {
                    // Jar-in-Jar：同一个模组被多处内嵌（或者也单独放进了 mods），取最高版本，版本相同时优先单独放的
                    ModCandidate pick = newest(list);
                    selected.add(pick);
                    List<String> others = list.stream().filter(c -> !c.version().equals(pick.version()))
                            .map(ModCandidate::describe).distinct().toList();
                    if (!others.isEmpty()) {
                        notes.add(Messages.tr("resolve.nestedDuplicate", id, pick.describe(),
                                String.join("; ", others)));
                    }
                    continue;
                }
                list = topLevel;
            }
            List<ModCandidate> fabric = list.stream().filter(c -> c.ecosystem() == Ecosystem.FABRIC).toList();
            List<ModCandidate> forgeFamily = list.stream().filter(c -> c.ecosystem().isForgeFamily()).toList();
            if (fabric.size() == 1 && !forgeFamily.isEmpty()) {
                selected.add(fabric.get(0));
                warnings.add(Messages.tr("resolve.crossDuplicate", id, fabric.get(0).describe(),
                        forgeFamily.stream().map(ModCandidate::describe).collect(Collectors.joining("; "))));
            } else {
                errors.add(Messages.tr("resolve.duplicate", id,
                        list.stream().map(c -> "  - " + c.describe()).collect(Collectors.joining("\n"))));
            }
        }
        return new Selection(selected, errors, warnings, notes);
    }

    private static boolean ancestorsChosen(ModCandidate c, Set<ModCandidate> chosen) {
        for (ModCandidate p = c.parent(); p != null; p = p.parent()) {
            if (!chosen.contains(p)) {
                return false;
            }
        }
        return true;
    }

    private static Set<ModCandidate> identitySet(List<ModCandidate> mods) {
        Set<ModCandidate> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(mods);
        return set;
    }

    private static void checkDependency(ModCandidate c, Dependency d, ModCandidate target,
                                        List<String> errors, List<String> warnings) {
        String want = d.predicate().describe();
        switch (d.kind()) {
            case REQUIRED:
                if (target == null) {
                    errors.add(Messages.tr("resolve.missing", c.describe(), d.modId(), want));
                } else if (!d.predicate().test(target.version())) {
                    errors.add(Messages.tr("resolve.wrongVersion", c.describe(), d.modId(), want, target.version()));
                }
                break;
            case OPTIONAL:
                if (target != null && !d.predicate().test(target.version())) {
                    errors.add(Messages.tr("resolve.optionalWrongVersion", c.describe(), d.modId(), want,
                            target.version()));
                }
                break;
            case RECOMMENDED:
                if (target == null) {
                    warnings.add(Messages.tr("resolve.recommendMissing", c.describe(), d.modId(), want));
                } else if (!d.predicate().test(target.version())) {
                    warnings.add(Messages.tr("resolve.recommendWrongVersion", c.describe(), d.modId(), want,
                            target.version()));
                }
                break;
            case INCOMPATIBLE:
                if (target != null && d.predicate().test(target.version())) {
                    errors.add(Messages.tr("resolve.incompatible", c.describe(), target.describe()));
                }
                break;
            case DISCOURAGED:
                if (target != null && d.predicate().test(target.version())) {
                    warnings.add(Messages.tr("resolve.discouraged", c.describe(), target.describe()));
                }
                break;
            case SUGGESTED:
            default:
                break;
        }
    }

    /** 拓扑排序：依赖先于被依赖者；没有约束的按 id 字母顺序；出现环时按字母顺序补上并警告。 */
    private static List<ModCandidate> order(List<ModCandidate> mods, Map<String, ModCandidate> byId,
                                            List<String> warnings) {
        Map<ModCandidate, Set<ModCandidate>> after = new HashMap<>(); // key 必须排在 value 里每一个之后
        for (ModCandidate m : mods) {
            after.put(m, new TreeSet<>(Comparator.comparing(ModCandidate::id)));
        }
        for (ModCandidate m : mods) {
            for (Dependency d : m.dependencies()) {
                ModCandidate target = byId.get(d.modId());
                if (target == null || target == m || !after.containsKey(target)) {
                    continue; // 内置模组或缺失的依赖不参与排序
                }
                boolean ordering = d.kind() == Dependency.Kind.REQUIRED || d.kind() == Dependency.Kind.OPTIONAL
                        || d.kind() == Dependency.Kind.RECOMMENDED || d.kind() == Dependency.Kind.SUGGESTED;
                if (!ordering) {
                    continue;
                }
                if (d.ordering() == Dependency.Ordering.BEFORE) {
                    after.get(target).add(m);
                } else if (d.ordering() == Dependency.Ordering.AFTER || d.kind() == Dependency.Kind.REQUIRED) {
                    after.get(m).add(target);
                }
            }
        }
        Map<ModCandidate, Integer> remaining = new HashMap<>();
        Map<ModCandidate, List<ModCandidate>> dependents = new HashMap<>();
        for (ModCandidate m : mods) {
            remaining.put(m, after.get(m).size());
            for (ModCandidate dep : after.get(m)) {
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(m);
            }
        }
        PriorityQueue<ModCandidate> ready = new PriorityQueue<>(Comparator.comparing(ModCandidate::id));
        for (ModCandidate m : mods) {
            if (remaining.get(m) == 0) {
                ready.add(m);
            }
        }
        List<ModCandidate> out = new ArrayList<>();
        while (!ready.isEmpty()) {
            ModCandidate m = ready.poll();
            out.add(m);
            for (ModCandidate next : dependents.getOrDefault(m, List.of())) {
                int left = remaining.merge(next, -1, Integer::sum);
                if (left == 0) {
                    ready.add(next);
                }
            }
        }
        if (out.size() < mods.size()) {
            List<ModCandidate> cyclic = mods.stream().filter(m -> !out.contains(m))
                    .sorted(Comparator.comparing(ModCandidate::id)).toList();
            warnings.add(Messages.tr("resolve.cycle",
                    cyclic.stream().map(ModCandidate::id).collect(Collectors.joining(", "))));
            out.addAll(cyclic);
        }
        return out;
    }

    /** 版本最高的一个；版本相同时优先不是内嵌的，再按发现顺序。 */
    static ModCandidate newest(List<ModCandidate> list) {
        ModCandidate best = list.get(0);
        for (ModCandidate c : list.subList(1, list.size())) {
            int cmp = compareVersions(c.version(), best.version());
            if (cmp > 0 || (cmp == 0 && best.isNested() && !c.isNested())) {
                best = c;
            }
        }
        return best;
    }

    /** 两边都是语义版本时按语义版本比，否则按 Maven 的规则比（Forge 系模组的版本号）。 */
    static int compareVersions(String a, String b) {
        SemanticVersion sa = SemanticVersion.tryParse(a);
        SemanticVersion sb = SemanticVersion.tryParse(b);
        if (sa != null && sb != null) {
            return sa.compareTo(sb);
        }
        return new MavenVersion(a).compareTo(new MavenVersion(b));
    }
}
