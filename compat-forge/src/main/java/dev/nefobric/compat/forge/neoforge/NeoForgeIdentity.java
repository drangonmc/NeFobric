package dev.nefobric.compat.forge.neoforge;

import dev.nefobric.loader.metadata.Dependency;
import dev.nefobric.loader.version.VersionPredicate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NeoForge 1.20.1 在依赖检查里的“身份”。
 *
 * <p>Minecraft 1.20.1 的 NeoForge（47.1.x，最后一版 47.1.106）是 Forge 47.1 的分支：主模组 id 仍是
 * {@code forge}，没有名为 {@code neoforge} 的模组；FML 用的是 FancyModLoader 47.2.x。NeFobric 的
 * Forge 系运行时两家共用，对外报的是 Forge 47.4.0 的版本号。为了让按 NeoForge 版本号写区间的模组也能
 * 通过检查，这里让几个内置虚拟模组同时带有 NeoForge 那一侧的版本号：</p>
 * <ul>
 *   <li>{@code forge}：47.4.0（Forge）或 47.1.106（NeoForge 最后一版）；</li>
 *   <li>{@code javafml} / {@code lowcodefml}：47、47.1、47.2、47.4。真实值是语言加载器 jar 的
 *       Implementation-Version：NeoForge 47.1.106（FML 47.2.2）是 47.2，早期 NeoForge 47.1 是 47.1，
 *       Forge 47.4 是 47 或 47.4。</li>
 * </ul>
 * <p>区间包含其中任意一个就算满足。这些值都在 1.20.1 的范围内，所以写给 1.20.2 以后（Forge 48+、
 * FML 1.x 以后）的区间仍然不满足。</p>
 *
 * <p>另外，NeoForge 从 20.2（Minecraft 1.20.2）起才有 {@code neoforge} 这个模组，版本号改成跟着
 * Minecraft 走（20.2、20.4、21.1……）。强制依赖 neoforge 且区间下限是这种版本号的模组只能是给
 * 1.20.2 及以上用的；虚拟的 neoforge 47.1.106 在数值上又恰好满足 {@code [20.4,)} 这类区间，
 * 所以要在发现阶段直接认出来并跳过，见 {@link #modernNeoForgeRange(String)}。</p>
 */
public final class NeoForgeIdentity {
    /** 和 {@code ForgeAdapter.EMULATED_FORGE_VERSION} 一起对外报告的 NeoForge 1.20.1 最后一版。 */
    public static final String NEOFORGE_1_20_1_LAST = "47.1.106";
    /** NeoForge 1.20.1 与 Forge 47.4 的 javafml / lowcodefml 可能报告的版本。 */
    public static final List<String> FML_LANGUAGE_VERSIONS = List.of("47", "47.1", "47.2", "47.4");

    /** 1.20.2 以后的 NeoForge 版本号的主版本范围（20.2 起，跟着 Minecraft 版本走）。 */
    private static final int MODERN_MAJOR_MIN = 20;
    /** 47 及以上是 1.20.1 的 Forge 系编号，不算。 */
    private static final int MODERN_MAJOR_MAX = 46;

    private static final Pattern RESTRICTION = Pattern.compile("[\\[(]([^\\])]*)[\\])]");
    private static final Pattern LEADING_INT = Pattern.compile("^\\s*(\\d+)");

    private NeoForgeIdentity() {
    }

    /**
     * 给 Forge 系模组对内置虚拟模组的依赖加上 NeoForge 一侧的版本号。
     *
     * @param target       依赖的模组 id
     * @param kind         依赖种类；只放宽“需要”类的依赖，“不兼容”类保持原样，免得误报冲突
     * @param reported     这个内置虚拟模组实际报告的版本（例如 forge 的 47.4.0）
     * @param predicate    mods.toml 里写的区间
     */
    public static VersionPredicate widen(String target, Dependency.Kind kind, String reported, VersionPredicate predicate) {
        if (kind == Dependency.Kind.INCOMPATIBLE || kind == Dependency.Kind.DISCOURAGED) {
            return predicate;
        }
        List<String> aliases = aliasesOf(target);
        if (aliases.isEmpty() || reported == null) {
            return predicate;
        }
        return new AliasedPredicate(predicate, reported, aliases);
    }

    /** 内置虚拟模组在 NeoForge 1.20.1 / Forge 47.4 上可能报告的其他版本号。 */
    public static List<String> aliasesOf(String target) {
        return ALIASES.getOrDefault(target, List.of());
    }

    private static final Map<String, List<String>> ALIASES = Map.of(
            "forge", List.of(NEOFORGE_1_20_1_LAST),
            "javafml", FML_LANGUAGE_VERSIONS,
            "lowcodefml", FML_LANGUAGE_VERSIONS);

    /**
     * 如果对 neoforge 的强制依赖区间只能由 1.20.2 以后的 NeoForge 满足（每一段区间的下限主版本号都在
     * 20 到 46 之间），返回这个区间的原文。
     */
    public static Optional<String> modernNeoForgeRange(String versionRange) {
        if (versionRange == null) {
            return Optional.empty();
        }
        String spec = versionRange.trim();
        if (!spec.isEmpty() && spec.charAt(0) != '[' && spec.charAt(0) != '(') {
            // 不带括号的版本号（Maven 的“推荐版本”）：Forge 当成任意版本，但写 20.4 这类号的只会是新版 NeoForge 模组
            return isModernMajor(spec) ? Optional.of(spec) : Optional.empty();
        }
        Matcher m = RESTRICTION.matcher(spec);
        boolean any = false;
        while (m.find()) {
            any = true;
            String body = m.group(1);
            int comma = body.indexOf(',');
            String lower = comma < 0 ? body : body.substring(0, comma);
            // 没有下限（例如 (,21)）或下限不是新版 NeoForge 的编号：交给正常的版本检查
            if (!isModernMajor(lower)) {
                return Optional.empty();
            }
        }
        return any ? Optional.of(spec) : Optional.empty();
    }

    private static boolean isModernMajor(String version) {
        Matcher lead = LEADING_INT.matcher(version);
        if (!lead.find()) {
            return false;
        }
        try {
            int major = Integer.parseInt(lead.group(1));
            return major >= MODERN_MAJOR_MIN && major <= MODERN_MAJOR_MAX;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 原区间满足实际版本，或者实际版本是 {@code reported} 且区间满足任一别名。 */
    private record AliasedPredicate(VersionPredicate delegate, String reported, List<String> aliases)
            implements VersionPredicate {
        @Override
        public boolean test(String version) {
            if (delegate.test(version)) {
                return true;
            }
            if (!reported.equals(version)) {
                return false;
            }
            for (String alias : aliases) {
                if (delegate.test(alias)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String describe() {
            return delegate.describe();
        }
    }
}
