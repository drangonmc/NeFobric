package dev.nefobric.compat.fabric.version;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionComparisonOperator;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 解析 Fabric 写法的版本条件。规则与 Fabric Loader 相同：空格分隔的多个条件同时满足；
 * {@code 1.20.x} 换成 {@code ~1.20-}，{@code 1.x} 换成 {@code ^1-}；非语义版本只能用含边界的运算符，并按等于处理。
 */
public final class VersionPredicateParser {
    private static final VersionComparisonOperator[] OPERATORS = VersionComparisonOperator.values();

    private VersionPredicateParser() {
    }

    public static VersionPredicate parse(String predicate) throws VersionParsingException {
        List<SingleVersionPredicate> predicateList = new ArrayList<>();
        for (String s : predicate.split(" ")) {
            s = s.trim();
            if (s.isEmpty() || s.equals("*")) {
                continue;
            }
            VersionComparisonOperator operator = VersionComparisonOperator.EQUAL;
            for (VersionComparisonOperator op : OPERATORS) {
                if (s.startsWith(op.getSerialized())) {
                    operator = op;
                    s = s.substring(op.getSerialized().length());
                    break;
                }
            }
            Version version = VersionParser.parse(s, true);
            if (version instanceof SemanticVersion semVer) {
                if (semVer.hasWildcard()) {
                    if (operator != VersionComparisonOperator.EQUAL) {
                        throw new VersionParsingException("Invalid predicate: " + predicate
                                + ", version ranges with wildcards (.X) require using the equality operator or no operator at all!");
                    }
                    int compCount = semVer.getVersionComponentCount();
                    operator = compCount == 2 ? VersionComparisonOperator.SAME_TO_NEXT_MAJOR
                            : VersionComparisonOperator.SAME_TO_NEXT_MINOR;
                    int[] newComponents = new int[compCount - 1];
                    for (int i = 0; i < compCount - 1; i++) {
                        newComponents[i] = semVer.getVersionComponent(i);
                    }
                    version = new SemanticVersionImpl(newComponents, "", semVer.getBuildKey().orElse(null));
                }
            } else if (!operator.isMinInclusive() && !operator.isMaxInclusive()) {
                throw new VersionParsingException("Invalid predicate: " + predicate
                        + ", version ranges need to be semantic version compatible to use operators that exclude the bound!");
            } else {
                operator = VersionComparisonOperator.EQUAL;
            }
            predicateList.add(new SingleVersionPredicate(operator, version));
        }
        if (predicateList.isEmpty()) {
            return AnyVersionPredicate.INSTANCE;
        } else if (predicateList.size() == 1) {
            return predicateList.get(0);
        } else {
            return new MultiVersionPredicate(predicateList);
        }
    }

    public static VersionPredicate getAny() {
        return AnyVersionPredicate.INSTANCE;
    }

    static final class AnyVersionPredicate implements VersionPredicate {
        static final VersionPredicate INSTANCE = new AnyVersionPredicate();

        private AnyVersionPredicate() {
        }

        @Override
        public boolean test(Version t) {
            return true;
        }

        @Override
        public List<? extends PredicateTerm> getTerms() {
            return Collections.emptyList();
        }

        @Override
        public VersionInterval getInterval() {
            return VersionInterval.INFINITE;
        }

        @Override
        public String toString() {
            return "*";
        }
    }

    static final class SingleVersionPredicate implements VersionPredicate, VersionPredicate.PredicateTerm {
        private final VersionComparisonOperator operator;
        private final Version refVersion;

        SingleVersionPredicate(VersionComparisonOperator operator, Version refVersion) {
            this.operator = operator;
            this.refVersion = refVersion;
        }

        @Override
        public boolean test(Version version) {
            Objects.requireNonNull(version, "null version");
            return operator.test(version, refVersion);
        }

        @Override
        public List<PredicateTerm> getTerms() {
            return Collections.singletonList(this);
        }

        @Override
        public VersionComparisonOperator getOperator() {
            return operator;
        }

        @Override
        public Version getReferenceVersion() {
            return refVersion;
        }

        @Override
        public VersionInterval getInterval() {
            if (refVersion instanceof SemanticVersion version) {
                return new VersionIntervalImpl(operator.minVersion(version), operator.isMinInclusive(),
                        operator.maxVersion(version), operator.isMaxInclusive());
            } else {
                return new VersionIntervalImpl(refVersion, true, refVersion, true);
            }
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SingleVersionPredicate o && operator == o.operator && refVersion.equals(o.refVersion);
        }

        @Override
        public int hashCode() {
            return operator.ordinal() * 31 + refVersion.hashCode();
        }

        @Override
        public String toString() {
            return operator.getSerialized() + refVersion.toString();
        }
    }

    static final class MultiVersionPredicate implements VersionPredicate {
        private final List<SingleVersionPredicate> predicates;

        MultiVersionPredicate(List<SingleVersionPredicate> predicates) {
            this.predicates = List.copyOf(predicates);
        }

        @Override
        public boolean test(Version version) {
            Objects.requireNonNull(version, "null version");
            for (SingleVersionPredicate predicate : predicates) {
                if (!predicate.test(version)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public List<? extends PredicateTerm> getTerms() {
            return predicates;
        }

        @Override
        public VersionInterval getInterval() {
            if (predicates.isEmpty()) {
                return AnyVersionPredicate.INSTANCE.getInterval();
            }
            VersionInterval ret = predicates.get(0).getInterval();
            for (int i = 1; i < predicates.size(); i++) {
                ret = VersionIntervalImpl.and(ret, predicates.get(i).getInterval());
            }
            return ret;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof MultiVersionPredicate o && predicates.equals(o.predicates);
        }

        @Override
        public int hashCode() {
            return predicates.hashCode();
        }

        @Override
        public String toString() {
            StringBuilder ret = new StringBuilder();
            for (SingleVersionPredicate p : predicates) {
                if (ret.length() > 0) {
                    ret.append(' ');
                }
                ret.append(p);
            }
            return ret.toString();
        }
    }

    /** 给内部用：一组条件里任意一个满足即可（fabric.mod.json 依赖值写成数组时的语义）。 */
    public static boolean anyMatches(Collection<VersionPredicate> predicates, Version version) {
        for (VersionPredicate p : predicates) {
            if (p.test(version)) {
                return true;
            }
        }
        return false;
    }
}
