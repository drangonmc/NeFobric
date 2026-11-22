package dev.nefobric.compat.fabric.version;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionInterval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 版本区间及其交、并、补运算。结果列表按下界升序、互不重叠。 */
public final class VersionIntervalImpl implements VersionInterval {
    private final Version min;
    private final boolean minInclusive;
    private final Version max;
    private final boolean maxInclusive;

    public VersionIntervalImpl(Version min, boolean minInclusive, Version max, boolean maxInclusive) {
        this.min = min;
        this.minInclusive = min != null && minInclusive;
        this.max = max;
        this.maxInclusive = max != null && maxInclusive;
    }

    @Override
    public boolean isSemantic() {
        return (min == null || min instanceof SemanticVersion) && (max == null || max instanceof SemanticVersion);
    }

    @Override
    public Version getMin() {
        return min;
    }

    @Override
    public boolean isMinInclusive() {
        return minInclusive;
    }

    @Override
    public Version getMax() {
        return max;
    }

    @Override
    public boolean isMaxInclusive() {
        return maxInclusive;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof VersionInterval o && Objects.equals(min, o.getMin()) && minInclusive == o.isMinInclusive()
                && Objects.equals(max, o.getMax()) && maxInclusive == o.isMaxInclusive();
    }

    @Override
    public int hashCode() {
        return (Objects.hashCode(min) + (minInclusive ? 1 : 0)) * 31 + (Objects.hashCode(max) + (maxInclusive ? 1 : 0)) * 31;
    }

    @Override
    public String toString() {
        if (min == null) {
            if (max == null) {
                return "(-∞,∞)";
            }
            return String.format("(-∞,%s%c", max, maxInclusive ? ']' : ')');
        } else if (max == null) {
            return String.format("%c%s,∞)", minInclusive ? '[' : '(', min);
        } else {
            return String.format("%c%s,%s%c", minInclusive ? '[' : '(', min, max, maxInclusive ? ']' : ')');
        }
    }

    public static VersionInterval and(VersionInterval a, VersionInterval b) {
        if (a == null || b == null) {
            return null;
        }
        if (!a.isSemantic() || !b.isSemantic()) {
            return andPlain(a, b);
        }
        Version min;
        boolean minInc;
        int cmpMin = compareMin(a, b);
        if (cmpMin >= 0) {
            min = a.getMin();
            minInc = a.isMinInclusive();
        } else {
            min = b.getMin();
            minInc = b.isMinInclusive();
        }
        if (a.getMin() != null && b.getMin() != null && a.getMin().compareTo(b.getMin()) == 0) {
            minInc = a.isMinInclusive() && b.isMinInclusive();
        }
        Version max;
        boolean maxInc;
        int cmpMax = compareMax(a, b);
        if (cmpMax <= 0) {
            max = a.getMax();
            maxInc = a.isMaxInclusive();
        } else {
            max = b.getMax();
            maxInc = b.isMaxInclusive();
        }
        if (a.getMax() != null && b.getMax() != null && a.getMax().compareTo(b.getMax()) == 0) {
            maxInc = a.isMaxInclusive() && b.isMaxInclusive();
        }
        if (min != null && max != null) {
            int c = min.compareTo(max);
            if (c > 0 || (c == 0 && (!minInc || !maxInc))) {
                return null;
            }
        }
        return new VersionIntervalImpl(min, minInc, max, maxInc);
    }

    private static VersionInterval andPlain(VersionInterval a, VersionInterval b) {
        Version aMin = a.getMin();
        Version aMax = a.getMax();
        Version bMin = b.getMin();
        Version bMax = b.getMax();
        if (aMin != null) {
            if (bMin != null && !aMin.equals(bMin) || bMax != null && !aMin.equals(bMax)) {
                return null;
            }
            if (aMax != null && !aMin.equals(aMax)) {
                return null;
            }
            return a;
        } else if (aMax != null) {
            if (bMin != null && !aMax.equals(bMin) || bMax != null && !aMax.equals(bMax)) {
                return null;
            }
            return a;
        } else {
            return b;
        }
    }

    /** 下界比较：没有下界的最小。返回正数表示 a 的下界更高。 */
    private static int compareMin(VersionInterval a, VersionInterval b) {
        if (a.getMin() == null) {
            return b.getMin() == null ? 0 : -1;
        }
        if (b.getMin() == null) {
            return 1;
        }
        int c = a.getMin().compareTo(b.getMin());
        if (c != 0) {
            return c;
        }
        // 同一个下界，不含的那个更高
        return Boolean.compare(!a.isMinInclusive(), !b.isMinInclusive());
    }

    /** 上界比较：没有上界的最大。返回负数表示 a 的上界更低。 */
    private static int compareMax(VersionInterval a, VersionInterval b) {
        if (a.getMax() == null) {
            return b.getMax() == null ? 0 : 1;
        }
        if (b.getMax() == null) {
            return -1;
        }
        int c = a.getMax().compareTo(b.getMax());
        if (c != 0) {
            return c;
        }
        return Boolean.compare(a.isMaxInclusive(), b.isMaxInclusive());
    }

    public static List<VersionInterval> and(Collection<VersionInterval> a, Collection<VersionInterval> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return Collections.emptyList();
        }
        if (a.size() == 1 && b.size() == 1) {
            VersionInterval merged = and(a.iterator().next(), b.iterator().next());
            return merged != null ? Collections.singletonList(merged) : Collections.emptyList();
        }
        List<VersionInterval> allMerged = new ArrayList<>();
        for (VersionInterval intervalA : a) {
            for (VersionInterval intervalB : b) {
                VersionInterval merged = and(intervalA, intervalB);
                if (merged != null) {
                    allMerged.add(merged);
                }
            }
        }
        if (allMerged.isEmpty()) {
            return Collections.emptyList();
        }
        if (allMerged.size() == 1) {
            return allMerged;
        }
        List<VersionInterval> ret = new ArrayList<>(allMerged.size());
        for (VersionInterval v : allMerged) {
            merge(v, ret);
        }
        return ret;
    }

    public static List<VersionInterval> or(Collection<VersionInterval> a, VersionInterval b) {
        if (a.isEmpty()) {
            return b == null ? Collections.emptyList() : Collections.singletonList(b);
        }
        List<VersionInterval> ret = new ArrayList<>(a.size() + 1);
        for (VersionInterval v : a) {
            merge(v, ret);
        }
        if (b != null) {
            merge(b, ret);
        }
        return ret;
    }

    /** 把 interval 并入有序不重叠的列表 out。 */
    private static void merge(VersionInterval interval, List<VersionInterval> out) {
        if (interval == null) {
            return;
        }
        if (!interval.isSemantic()) {
            if (!out.contains(interval)) {
                out.add(interval);
            }
            return;
        }
        List<VersionInterval> semantic = new ArrayList<>();
        List<VersionInterval> plain = new ArrayList<>();
        for (VersionInterval v : out) {
            (v.isSemantic() ? semantic : plain).add(v);
        }
        semantic.add(interval);
        semantic.sort(Comparator.comparing((VersionInterval v) -> v, VersionIntervalImpl::compareMin));
        List<VersionInterval> merged = new ArrayList<>();
        for (VersionInterval v : semantic) {
            if (merged.isEmpty()) {
                merged.add(v);
                continue;
            }
            VersionInterval last = merged.get(merged.size() - 1);
            if (overlapsOrTouches(last, v)) {
                merged.set(merged.size() - 1, union(last, v));
            } else {
                merged.add(v);
            }
        }
        out.clear();
        out.addAll(merged);
        out.addAll(plain);
    }

    /** last 的下界不高于 v 的下界时，两者是否相交或首尾相接。 */
    private static boolean overlapsOrTouches(VersionInterval last, VersionInterval v) {
        if (last.getMax() == null || v.getMin() == null) {
            return true;
        }
        int c = last.getMax().compareTo(v.getMin());
        return c > 0 || (c == 0 && (last.isMaxInclusive() || v.isMinInclusive()));
    }

    private static VersionInterval union(VersionInterval a, VersionInterval b) {
        Version max;
        boolean maxInc;
        if (compareMax(a, b) >= 0) {
            max = a.getMax();
            maxInc = a.isMaxInclusive();
        } else {
            max = b.getMax();
            maxInc = b.isMaxInclusive();
        }
        return new VersionIntervalImpl(a.getMin(), a.isMinInclusive(), max, maxInc);
    }

    public static List<VersionInterval> not(VersionInterval interval) {
        if (interval == null) {
            return Collections.singletonList(INFINITE);
        } else if (interval.getMin() == null) {
            if (interval.getMax() == null) {
                return Collections.emptyList();
            }
            return Collections.singletonList(new VersionIntervalImpl(interval.getMax(), !interval.isMaxInclusive(), null, false));
        } else if (interval.getMax() == null) {
            return Collections.singletonList(new VersionIntervalImpl(null, false, interval.getMin(), !interval.isMinInclusive()));
        } else if (interval.getMin().equals(interval.getMax()) && !interval.isSemantic()) {
            // 非语义版本只能表示“不等于”，没法用区间写出来
            return Collections.singletonList(INFINITE);
        } else {
            List<VersionInterval> ret = new ArrayList<>(2);
            ret.add(new VersionIntervalImpl(null, false, interval.getMin(), !interval.isMinInclusive()));
            ret.add(new VersionIntervalImpl(interval.getMax(), !interval.isMaxInclusive(), null, false));
            return ret;
        }
    }

    public static List<VersionInterval> not(Collection<VersionInterval> intervals) {
        if (intervals.isEmpty()) {
            return Collections.singletonList(INFINITE);
        }
        if (intervals.size() == 1) {
            return not(intervals.iterator().next());
        }
        // ¬(A ∪ B) = ¬A ∩ ¬B
        List<VersionInterval> ret = null;
        for (VersionInterval v : intervals) {
            List<VersionInterval> inverted = not(v);
            ret = ret == null ? inverted : and(ret, inverted);
            if (ret.isEmpty()) {
                break;
            }
        }
        return ret;
    }
}
