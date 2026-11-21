package dev.nefobric.loader.version;

import dev.nefobric.loader.util.Messages;

import java.util.List;

/**
 * 版本条件。每个生态用自己的语义解释版本号：Fabric 的条件按 SemVer 比较，Forge 系的条件按 Maven 区间比较。
 */
public interface VersionPredicate {

    boolean test(String version);

    /** 给玩家看的写法，例如 “>=0.14.21” 或 “[47,)”。 */
    String describe();

    static VersionPredicate any() {
        return Any.INSTANCE;
    }

    /** 任意一个条件满足即可（Fabric 依赖值写成数组时的语义）。 */
    static VersionPredicate anyOf(List<VersionPredicate> predicates) {
        if (predicates.isEmpty()) {
            return any();
        }
        if (predicates.size() == 1) {
            return predicates.get(0);
        }
        List<VersionPredicate> copy = List.copyOf(predicates);
        return new VersionPredicate() {
            @Override
            public boolean test(String version) {
                for (VersionPredicate p : copy) {
                    if (p.test(version)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String describe() {
                StringBuilder sb = new StringBuilder();
                for (VersionPredicate p : copy) {
                    if (sb.length() > 0) {
                        sb.append(" || ");
                    }
                    sb.append(p.describe());
                }
                return sb.toString();
            }
        };
    }

    final class Any implements VersionPredicate {
        static final Any INSTANCE = new Any();

        private Any() {
        }

        @Override
        public boolean test(String version) {
            return true;
        }

        @Override
        public String describe() {
            return Messages.tr("predicate.any");
        }
    }
}
