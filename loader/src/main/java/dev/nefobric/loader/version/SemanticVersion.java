package dev.nefobric.loader.version;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Fabric 语义的版本号：任意个数字段 + 可选预发布标签（-beta.1）+ 可选构建信息（+mc1.20.1，比较时忽略）。
 * 缺少的数字段按 0 处理，所以 1.20 与 1.20.0 相等。
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {
    /** 条件里的通配段（x、X 或 *）。 */
    public static final int WILDCARD = -1;

    private static final Pattern NUMBER = Pattern.compile("[0-9]+");
    private static final Pattern IDENTIFIERS = Pattern.compile("[0-9A-Za-z-]+(\\.[0-9A-Za-z-]+)*");

    private final int[] components;
    private final String prerelease;
    private final String build;

    private SemanticVersion(int[] components, String prerelease, String build) {
        this.components = components;
        this.prerelease = prerelease;
        this.build = build;
    }

    public static SemanticVersion parse(String text, boolean allowWildcard) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("empty version");
        }
        String core = text;
        String build = null;
        String pre = null;
        int plus = core.indexOf('+');
        if (plus >= 0) {
            build = core.substring(plus + 1);
            core = core.substring(0, plus);
            if (!IDENTIFIERS.matcher(build).matches()) {
                throw new IllegalArgumentException("invalid build metadata in " + text);
            }
        }
        int dash = core.indexOf('-');
        if (dash >= 0) {
            pre = core.substring(dash + 1);
            core = core.substring(0, dash);
            if (!IDENTIFIERS.matcher(pre).matches()) {
                throw new IllegalArgumentException("invalid pre-release in " + text);
            }
        }
        if (core.isEmpty() || core.startsWith(".") || core.endsWith(".")) {
            throw new IllegalArgumentException("invalid version " + text);
        }
        String[] parts = core.split("\\.");
        int[] comps = new int[parts.length];
        boolean wildcard = false;
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (allowWildcard && (p.equals("x") || p.equals("X") || p.equals("*"))) {
                comps[i] = WILDCARD;
                wildcard = true;
                continue;
            }
            if (wildcard) {
                throw new IllegalArgumentException("wildcard must be the last part in " + text);
            }
            if (!NUMBER.matcher(p).matches()) {
                throw new IllegalArgumentException("invalid version part '" + p + "' in " + text);
            }
            try {
                comps[i] = Integer.parseInt(p);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("version part too large in " + text, e);
            }
        }
        if (wildcard && pre != null) {
            throw new IllegalArgumentException("wildcard versions cannot have a pre-release: " + text);
        }
        return new SemanticVersion(comps, pre, build);
    }

    /** 不是合法语义版本时返回 {@code null}。 */
    public static SemanticVersion tryParse(String text) {
        try {
            return parse(text, false);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public int componentCount() {
        return components.length;
    }

    /** 第 i 段；超出长度的段视为 0。 */
    public int component(int i) {
        return i < components.length ? components[i] : 0;
    }

    public boolean hasWildcard() {
        for (int c : components) {
            if (c == WILDCARD) {
                return true;
            }
        }
        return false;
    }

    public int wildcardIndex() {
        for (int i = 0; i < components.length; i++) {
            if (components[i] == WILDCARD) {
                return i;
            }
        }
        return -1;
    }

    public String prerelease() {
        return prerelease;
    }

    public String build() {
        return build;
    }

    /** 只比较数字段。 */
    public int compareCore(int[] other) {
        int n = Math.max(components.length, other.length);
        for (int i = 0; i < n; i++) {
            int a = component(i);
            int b = i < other.length ? other[i] : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    int[] componentsCopy() {
        return Arrays.copyOf(components, components.length);
    }

    @Override
    public int compareTo(SemanticVersion o) {
        int c = compareCore(o.components);
        if (c != 0) {
            return c;
        }
        if (prerelease == null) {
            return o.prerelease == null ? 0 : 1;
        }
        if (o.prerelease == null) {
            return -1;
        }
        return comparePrerelease(prerelease, o.prerelease);
    }

    private static int comparePrerelease(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.min(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            boolean na = NUMBER.matcher(pa[i]).matches();
            boolean nb = NUMBER.matcher(pb[i]).matches();
            int c;
            if (na && nb) {
                c = compareNumericStrings(pa[i], pb[i]);
            } else if (na) {
                c = -1;
            } else if (nb) {
                c = 1;
            } else {
                c = pa[i].compareTo(pb[i]);
            }
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(pa.length, pb.length);
    }

    private static int compareNumericStrings(String a, String b) {
        String sa = a.replaceFirst("^0+(?=.)", "");
        String sb = b.replaceFirst("^0+(?=.)", "");
        if (sa.length() != sb.length()) {
            return Integer.compare(sa.length(), sb.length());
        }
        return sa.compareTo(sb);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SemanticVersion v && compareTo(v) == 0;
    }

    @Override
    public int hashCode() {
        int n = components.length;
        while (n > 0 && components[n - 1] == 0) {
            n--;
        }
        int h = Arrays.hashCode(Arrays.copyOf(components, n));
        return 31 * h + (prerelease == null ? 0 : prerelease.hashCode());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(components[i] == WILDCARD ? "x" : String.valueOf(components[i]));
        }
        if (prerelease != null) {
            sb.append('-').append(prerelease);
        }
        if (build != null) {
            sb.append('+').append(build);
        }
        return sb.toString();
    }
}
