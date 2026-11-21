package dev.nefobric.loader.metadata;

import dev.nefobric.loader.version.VersionPredicate;

import java.util.Objects;

/**
 * 统一的依赖声明。Fabric 的 depends / recommends / suggests / breaks / conflicts 和 Forge 的
 * mandatory / type 都映射到 {@link Kind}。
 */
public record Dependency(String modId, Kind kind, VersionPredicate predicate, SideRestriction side, Ordering ordering) {

    public enum Kind {
        /** 必须存在且版本匹配（Fabric depends，Forge mandatory=true / type=required）。 */
        REQUIRED,
        /** 可以不存在，存在时版本必须匹配（Forge mandatory=false / type=optional）。 */
        OPTIONAL,
        /** 缺失或版本不符时只警告（Fabric recommends）。 */
        RECOMMENDED,
        /** 仅作提示，不检查（Fabric suggests）。 */
        SUGGESTED,
        /** 存在且版本匹配时拒绝启动（Fabric breaks，Forge type=incompatible）。 */
        INCOMPATIBLE,
        /** 存在且版本匹配时警告（Fabric conflicts，Forge type=discouraged）。 */
        DISCOURAGED
    }

    /** 加载顺序要求，来自 Forge 的 ordering 字段。 */
    public enum Ordering { NONE, BEFORE, AFTER }

    public Dependency {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(predicate, "predicate");
        side = side == null ? SideRestriction.ANY : side;
        ordering = ordering == null ? Ordering.NONE : ordering;
    }

    public static Dependency of(String modId, Kind kind, VersionPredicate predicate) {
        return new Dependency(modId, kind, predicate, SideRestriction.ANY, Ordering.NONE);
    }
}
