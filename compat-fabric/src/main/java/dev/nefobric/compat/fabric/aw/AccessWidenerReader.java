package dev.nefobric.compat.fabric.aw;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析 access widener 文件（v1、v2），格式与错误规则对齐 Fabric 的 access-widener 库：
 * <pre>
 * accessWidener v2 intermediary
 * accessible class net/minecraft/class_1234
 * extendable method net/minecraft/class_1 method_2 (I)V
 * transitive-mutable field net/minecraft/class_1 field_3 I
 * </pre>
 */
public final class AccessWidenerReader {
    private AccessWidenerReader() {
    }

    public static final class FormatException extends Exception {
        private static final long serialVersionUID = 1L;

        FormatException(int line, String message) {
            super("line " + line + ": " + message);
        }
    }

    /** 只读第一行，返回命名空间；不是 access widener 文件时返回 {@code null}。 */
    public static String readNamespace(String text) {
        for (String raw : text.split("\r?\n", -1)) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] t = line.split("\\s+");
            return t.length == 3 && t[0].equals("accessWidener") ? t[2] : null;
        }
        return null;
    }

    public static AccessWidener read(String text) throws FormatException {
        String[] lines = text.split("\r?\n", -1);
        int lineNo = 0;
        String namespace = null;
        int version = 0;
        List<AccessWidener.Entry> entries = new ArrayList<>();
        for (String raw : lines) {
            lineNo++;
            String line = stripComment(raw);
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] t = line.trim().split("\\s+");
            if (namespace == null) {
                if (t.length != 3 || !t[0].equals("accessWidener")) {
                    throw new FormatException(lineNo, "Invalid access widener file header. Expected: 'accessWidener <version> <namespace>'");
                }
                switch (t[1]) {
                    case "v1":
                        version = 1;
                        break;
                    case "v2":
                        version = 2;
                        break;
                    default:
                        throw new FormatException(lineNo, "Unsupported access widener format: " + t[1]);
                }
                namespace = t[2];
                continue;
            }
            entries.add(parseEntry(lineNo, t, version));
        }
        if (namespace == null) {
            throw new FormatException(lineNo, "Missing access widener file header");
        }
        return new AccessWidener(namespace, version, entries);
    }

    private static AccessWidener.Entry parseEntry(int lineNo, String[] t, int version) throws FormatException {
        if (t.length < 3) {
            throw new FormatException(lineNo, "Expected (<access> class <className>) or (<access> <method|field> <className> <name> <desc>)");
        }
        String accessToken = t[0];
        boolean transitive = false;
        if (accessToken.startsWith("transitive-")) {
            if (version < 2) {
                throw new FormatException(lineNo, "The transitive keyword is not supported in access widener v1");
            }
            transitive = true;
            accessToken = accessToken.substring("transitive-".length());
        }
        AccessWidener.Access access;
        switch (accessToken) {
            case "accessible":
                access = AccessWidener.Access.ACCESSIBLE;
                break;
            case "extendable":
                access = AccessWidener.Access.EXTENDABLE;
                break;
            case "mutable":
                access = AccessWidener.Access.MUTABLE;
                break;
            default:
                throw new FormatException(lineNo, "Unknown access type: " + t[0]);
        }
        switch (t[1]) {
            case "class":
                if (t.length != 3) {
                    throw new FormatException(lineNo, "Expected (<access> class <className>) got " + String.join(" ", t));
                }
                if (access == AccessWidener.Access.MUTABLE) {
                    throw new FormatException(lineNo, "Class can only be accessible or extendable");
                }
                checkClassName(lineNo, t[2]);
                return new AccessWidener.Entry(AccessWidener.Target.CLASS, access, transitive, t[2], null, null);
            case "method":
                if (t.length != 5) {
                    throw new FormatException(lineNo, "Expected (<access> method <className> <methodName> <methodDesc>) got " + String.join(" ", t));
                }
                if (access == AccessWidener.Access.MUTABLE) {
                    throw new FormatException(lineNo, "Method can only be accessible or extendable");
                }
                checkClassName(lineNo, t[2]);
                if (!t[4].startsWith("(")) {
                    throw new FormatException(lineNo, "Invalid method descriptor: " + t[4]);
                }
                return new AccessWidener.Entry(AccessWidener.Target.METHOD, access, transitive, t[2], t[3], t[4]);
            case "field":
                if (t.length != 5) {
                    throw new FormatException(lineNo, "Expected (<access> field <className> <fieldName> <fieldDesc>) got " + String.join(" ", t));
                }
                if (access == AccessWidener.Access.EXTENDABLE) {
                    throw new FormatException(lineNo, "Field can only be accessible or mutable");
                }
                checkClassName(lineNo, t[2]);
                return new AccessWidener.Entry(AccessWidener.Target.FIELD, access, transitive, t[2], t[3], t[4]);
            default:
                throw new FormatException(lineNo, "Unsupported type: '" + t[1] + "'");
        }
    }

    private static void checkClassName(int lineNo, String name) throws FormatException {
        if (name.indexOf('.') >= 0) {
            throw new FormatException(lineNo, "Class-names must be specified as a/b/C, not a.b.C, but found: " + name);
        }
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }
}
