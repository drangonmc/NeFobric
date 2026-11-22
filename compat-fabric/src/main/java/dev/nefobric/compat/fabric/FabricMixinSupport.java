package dev.nefobric.compat.fabric;

import dev.nefobric.compat.fabric.metadata.FabricModMetadata;
import dev.nefobric.compat.fabric.version.VersionParser;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import org.spongepowered.asm.mixin.FabricUtil;

import java.util.Collections;
import java.util.List;

/**
 * Fabric 对 Mixin 配置的装饰：每个配置记下所属模组 id，并按模组依赖的 fabricloader 最低版本选择 Mixin 兼容等级
 * （老模组按老版本 Fabric Mixin 的行为处理）。算法与 Fabric Loader 0.16 的 FabricMixinBootstrap 相同。
 */
public final class FabricMixinSupport {
    /** 加载器版本与对应的 Mixin 兼容等级，从新到旧。 */
    private static final List<Object[]> VERSIONS = List.of(
            new Object[]{VersionParser.parseLenient("0.16.0"), FabricUtil.COMPATIBILITY_0_14_0},
            new Object[]{VersionParser.parseLenient("0.12.0-"), FabricUtil.COMPATIBILITY_0_10_0});

    private FabricMixinSupport() {
    }

    public static String modIdKey() {
        return FabricUtil.KEY_MOD_ID;
    }

    public static String compatibilityKey() {
        return FabricUtil.KEY_COMPATIBILITY;
    }

    public static int compatibilityFor(FabricModMetadata meta) {
        List<VersionInterval> reqIntervals = Collections.singletonList(VersionInterval.INFINITE);
        for (ModDependency dep : meta.getDependencies()) {
            if (dep.getModId().equals("fabricloader") || dep.getModId().equals("fabric-loader")) {
                if (dep.getKind() == ModDependency.Kind.DEPENDS) {
                    reqIntervals = VersionInterval.and(reqIntervals, dep.getVersionIntervals());
                } else if (dep.getKind() == ModDependency.Kind.BREAKS) {
                    reqIntervals = VersionInterval.and(reqIntervals, VersionInterval.not(dep.getVersionIntervals()));
                }
            }
        }
        if (reqIntervals.isEmpty()) {
            // 模组和所有加载器版本都不兼容，依赖检查会先报错；这里给最新等级
            return FabricUtil.COMPATIBILITY_LATEST;
        }
        Version minLoaderVersion = reqIntervals.get(0).getMin();
        if (minLoaderVersion != null) {
            for (Object[] v : VERSIONS) {
                if (minLoaderVersion.compareTo((Version) v[0]) >= 0) {
                    return (Integer) v[1];
                }
            }
        }
        return FabricUtil.COMPATIBILITY_0_9_2;
    }
}
