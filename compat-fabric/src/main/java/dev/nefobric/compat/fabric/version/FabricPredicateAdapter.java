package dev.nefobric.compat.fabric.version;

import dev.nefobric.loader.version.VersionPredicate;
import net.fabricmc.loader.api.metadata.ModDependency;

/**
 * 把 Fabric 的依赖条件交给核心的依赖解析器用。被依赖的模组可能来自任何生态，版本号一律按 Fabric 规则解析
 * （不是语义版本时按字符串）。
 */
public final class FabricPredicateAdapter implements VersionPredicate {
    private final ModDependency dependency;
    private final String text;

    public FabricPredicateAdapter(ModDependency dependency, String text) {
        this.dependency = dependency;
        // “*” 用核心的“任意版本”说法，报错信息和其他生态一致
        this.text = text.trim().equals("*") || text.trim().isEmpty() ? VersionPredicate.any().describe() : text;
    }

    @Override
    public boolean test(String version) {
        return dependency.matches(VersionParser.parseLenient(version));
    }

    @Override
    public String describe() {
        return text;
    }

    @Override
    public String toString() {
        return text;
    }
}
