package dev.nefobric.loader.version;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maven 语义的版本号（Forge 与 NeoForge 用的规则，简化版 ComparableVersion）：
 * 按点、连字符以及数字和字母的交界切开，数字段按数值比较，限定词按 alpha &lt; beta &lt; milestone &lt; rc
 * &lt; snapshot &lt; 正式版 &lt; sp 比较，末尾的 0 和空限定词不影响结果（1.0 == 1.0.0）。
 */
public final class MavenVersion implements Comparable<MavenVersion> {
    private static final List<String> QUALIFIERS = List.of("alpha", "beta", "milestone", "rc", "snapshot", "", "sp");
    private static final int RELEASE_RANK = QUALIFIERS.indexOf("");

    private final String raw;
    private final List<Object> items;

    public MavenVersion(String raw) {
        this.raw = raw;
        this.items = tokenize(raw);
    }

    private static List<Object> tokenize(String raw) {
        List<Object> out = new ArrayList<>();
        String s = raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder cur = new StringBuilder();
        Boolean curDigit = null;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '.' || ch == '-' || ch == '_' || ch == '+') {
                flush(out, cur, curDigit);
                curDigit = null;
                continue;
            }
            boolean digit = Character.isDigit(ch);
            if (curDigit != null && curDigit != digit) {
                flush(out, cur, curDigit);
            }
            cur.append(ch);
            curDigit = digit;
        }
        flush(out, cur, curDigit);
        // 去掉末尾不影响比较的 0 和正式版限定词
        while (!out.isEmpty()) {
            Object last = out.get(out.size() - 1);
            if ((last instanceof BigInteger b && b.signum() == 0) || "".equals(last)) {
                out.remove(out.size() - 1);
            } else {
                break;
            }
        }
        return out;
    }

    private static void flush(List<Object> out, StringBuilder cur, Boolean digit) {
        if (cur.length() == 0) {
            return;
        }
        String token = cur.toString();
        cur.setLength(0);
        if (Boolean.TRUE.equals(digit)) {
            out.add(new BigInteger(token));
        } else {
            out.add(normalizeQualifier(token));
        }
    }

    private static String normalizeQualifier(String q) {
        switch (q) {
            case "a":
                return "alpha";
            case "b":
                return "beta";
            case "m":
                return "milestone";
            case "cr":
                return "rc";
            case "ga":
            case "final":
            case "release":
                return "";
            default:
                return q;
        }
    }

    @Override
    public int compareTo(MavenVersion o) {
        int n = Math.max(items.size(), o.items.size());
        for (int i = 0; i < n; i++) {
            Object a = i < items.size() ? items.get(i) : null;
            Object b = i < o.items.size() ? o.items.get(i) : null;
            int c = compareItems(a, b);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static int compareItems(Object a, Object b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -compareItems(b, null);
        }
        if (a instanceof BigInteger ia) {
            if (b == null) {
                return ia.signum() == 0 ? 0 : 1;
            }
            if (b instanceof BigInteger ib) {
                return ia.compareTo(ib);
            }
            return 1; // 数字段比限定词大：1.0.1 > 1.0-rc1
        }
        String sa = (String) a;
        if (b == null) {
            return Integer.compare(rank(sa), RELEASE_RANK);
        }
        if (b instanceof BigInteger) {
            return -1;
        }
        String sb = (String) b;
        int ra = rank(sa);
        int rb = rank(sb);
        if (ra != rb) {
            return Integer.compare(ra, rb);
        }
        return ra == QUALIFIERS.size() ? sa.compareTo(sb) : 0;
    }

    /** 已知限定词的顺位；未知限定词排在 sp 之后，彼此按字母比较。 */
    private static int rank(String q) {
        int idx = QUALIFIERS.indexOf(q);
        return idx >= 0 ? idx : QUALIFIERS.size();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MavenVersion v && compareTo(v) == 0;
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }

    @Override
    public String toString() {
        return raw;
    }
}
