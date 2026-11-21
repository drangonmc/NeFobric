package dev.nefobric.loader.version;

import java.util.ArrayList;
import java.util.List;

/**
 * Forge {@code versionRange} 写法：{@code [47,)}、{@code [1.20.1,1.21)}、{@code (,2.0]}、{@code [1.0]}、
 * 多个区间用逗号连接（满足任一即可）。不带括号的版本号（如 {@code 1.0}）和 {@code *} 表示任意版本，
 * 与 Forge 的处理一致。
 */
public final class MavenVersionRange implements VersionPredicate {

    private record Restriction(MavenVersion lower, boolean lowerInclusive, MavenVersion upper, boolean upperInclusive) {
        boolean contains(MavenVersion v) {
            if (lower != null) {
                int c = v.compareTo(lower);
                if (c < 0 || (c == 0 && !lowerInclusive)) {
                    return false;
                }
            }
            if (upper != null) {
                int c = v.compareTo(upper);
                if (c > 0 || (c == 0 && !upperInclusive)) {
                    return false;
                }
            }
            return true;
        }
    }

    private final String text;
    private final List<Restriction> restrictions;

    private MavenVersionRange(String text, List<Restriction> restrictions) {
        this.text = text;
        this.restrictions = restrictions;
    }

    public static VersionPredicate parse(String spec) {
        String s = spec == null ? "" : spec.trim();
        if (s.isEmpty() || s.equals("*") || !(s.startsWith("[") || s.startsWith("("))) {
            return VersionPredicate.any();
        }
        List<Restriction> list = new ArrayList<>();
        String rest = s;
        while (!rest.isEmpty()) {
            char open = rest.charAt(0);
            if (open != '[' && open != '(') {
                throw new IllegalArgumentException("invalid version range: " + spec);
            }
            int close = indexOfClose(rest);
            if (close < 0) {
                throw new IllegalArgumentException("unclosed version range: " + spec);
            }
            char closeCh = rest.charAt(close);
            String body = rest.substring(1, close).trim();
            boolean lowerInc = open == '[';
            boolean upperInc = closeCh == ']';
            int comma = body.indexOf(',');
            if (comma < 0) {
                if (!lowerInc || !upperInc || body.isEmpty()) {
                    throw new IllegalArgumentException("a single version must use [x]: " + spec);
                }
                MavenVersion v = new MavenVersion(body);
                list.add(new Restriction(v, true, v, true));
            } else {
                String lo = body.substring(0, comma).trim();
                String hi = body.substring(comma + 1).trim();
                if (hi.contains(",")) {
                    throw new IllegalArgumentException("invalid version range: " + spec);
                }
                MavenVersion lower = lo.isEmpty() ? null : new MavenVersion(lo);
                MavenVersion upper = hi.isEmpty() ? null : new MavenVersion(hi);
                if (lower != null && upper != null && lower.compareTo(upper) > 0) {
                    throw new IllegalArgumentException("lower bound is above upper bound: " + spec);
                }
                list.add(new Restriction(lower, lowerInc, upper, upperInc));
            }
            rest = rest.substring(close + 1).trim();
            if (rest.startsWith(",")) {
                rest = rest.substring(1).trim();
                if (rest.isEmpty()) {
                    throw new IllegalArgumentException("trailing comma in version range: " + spec);
                }
            } else if (!rest.isEmpty()) {
                throw new IllegalArgumentException("invalid version range: " + spec);
            }
        }
        return new MavenVersionRange(s, List.copyOf(list));
    }

    private static int indexOfClose(String s) {
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ']' || c == ')') {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean test(String version) {
        MavenVersion v = new MavenVersion(version);
        for (Restriction r : restrictions) {
            if (r.contains(v)) {
                return true;
            }
        }
        return false;
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
