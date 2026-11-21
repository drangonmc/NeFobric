package dev.nefobric.loader.version;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric 写法的版本条件：{@code *}、{@code 1.2.3}、{@code =1.2.3}、{@code >=}、{@code >}、{@code <=}、{@code <}、
 * {@code ~1.20.1}（同主版本和次版本）、{@code ^1.2.3}（同主版本）、{@code 1.20.x}（通配），
 * 以及用空格连接的多个条件（同时满足）。
 */
public final class FabricVersionPredicate implements VersionPredicate {

    private enum Op { EQ, WILDCARD_EQ, GE, GT, LE, LT, TILDE, CARET }

    private record Term(Op op, SemanticVersion version, String raw) {
    }

    private final String text;
    private final List<Term> terms;

    private FabricVersionPredicate(String text, List<Term> terms) {
        this.text = text;
        this.terms = terms;
    }

    public static VersionPredicate parse(String expression) {
        String t = expression == null ? "" : expression.trim();
        if (t.isEmpty() || t.equals("*")) {
            return VersionPredicate.any();
        }
        List<Term> terms = new ArrayList<>();
        for (String part : t.split("\\s+")) {
            terms.add(parseTerm(part, t));
        }
        return new FabricVersionPredicate(t, List.copyOf(terms));
    }

    private static Term parseTerm(String part, String whole) {
        Op op = Op.EQ;
        String v = part;
        if (part.startsWith(">=")) {
            op = Op.GE;
            v = part.substring(2);
        } else if (part.startsWith("<=")) {
            op = Op.LE;
            v = part.substring(2);
        } else if (part.startsWith(">")) {
            op = Op.GT;
            v = part.substring(1);
        } else if (part.startsWith("<")) {
            op = Op.LT;
            v = part.substring(1);
        } else if (part.startsWith("=")) {
            v = part.substring(1);
        } else if (part.startsWith("~")) {
            op = Op.TILDE;
            v = part.substring(1);
        } else if (part.startsWith("^")) {
            op = Op.CARET;
            v = part.substring(1);
        }
        if (v.isEmpty()) {
            throw new IllegalArgumentException("invalid version predicate: " + whole);
        }
        SemanticVersion sv;
        try {
            sv = SemanticVersion.parse(v, true);
        } catch (IllegalArgumentException e) {
            if (op == Op.EQ) {
                // 不是语义版本（例如 “1.20.1-forge”之外的自定义写法）：按字符串比较
                return new Term(Op.EQ, null, v);
            }
            throw new IllegalArgumentException("invalid version predicate: " + whole, e);
        }
        if (sv.hasWildcard()) {
            if (op != Op.EQ) {
                throw new IllegalArgumentException("wildcards only work without an operator: " + whole);
            }
            op = Op.WILDCARD_EQ;
        }
        return new Term(op, sv, v);
    }

    @Override
    public boolean test(String version) {
        SemanticVersion v = SemanticVersion.tryParse(version);
        for (Term term : terms) {
            if (!matches(term, version, v)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(Term term, String raw, SemanticVersion v) {
        if (term.version == null) {
            return raw.equals(term.raw);
        }
        if (v == null) {
            return false;
        }
        SemanticVersion t = term.version;
        switch (term.op) {
            case EQ:
                return v.compareTo(t) == 0;
            case GE:
                return v.compareTo(t) >= 0;
            case GT:
                return v.compareTo(t) > 0;
            case LE:
                return v.compareTo(t) <= 0;
            case LT:
                return v.compareTo(t) < 0;
            case TILDE: {
                if (v.compareTo(t) < 0) {
                    return false;
                }
                int bump = Math.min(1, t.componentCount() - 1);
                return v.compareCore(upperBound(t, bump)) < 0;
            }
            case CARET:
                return v.compareTo(t) >= 0 && v.compareCore(upperBound(t, 0)) < 0;
            case WILDCARD_EQ: {
                int w = t.wildcardIndex();
                for (int i = 0; i < w; i++) {
                    if (v.component(i) != t.component(i)) {
                        return false;
                    }
                }
                return true;
            }
            default:
                return false;
        }
    }

    /** 把第 index 段加一、后面的段清零，得到不含的上界。 */
    private static int[] upperBound(SemanticVersion t, int index) {
        int[] upper = new int[index + 1];
        for (int i = 0; i < index; i++) {
            upper[i] = t.component(i);
        }
        upper[index] = t.component(index) + 1;
        return upper;
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
