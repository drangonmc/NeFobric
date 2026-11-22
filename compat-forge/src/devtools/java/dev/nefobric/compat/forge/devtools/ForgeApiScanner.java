package dev.nefobric.compat.forge.devtools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 开发工具：扫描 Forge 系模组 jar（含 META-INF/jarjar 里的内嵌 jar），列出它用到的东西，用来决定兼容层先实现什么。
 *
 * <ul>
 *   <li>引用的 {@code net/minecraftforge/**} 类、方法、字段（以及继承、实现的 Forge 类型）</li>
 *   <li>{@code @SubscribeEvent} 方法监听的事件类型、{@code addListener} 传入的 lambda 的事件类型</li>
 *   <li>对原版类的成员引用里，原版（SRG 命名的游戏 jar）不存在的那些：多半是 Forge 补丁加的方法</li>
 *   <li>Mixin 配置和目标类、访问变换（AT）、内嵌 jar</li>
 * </ul>
 *
 * <p>用法：{@code ForgeApiScanner [--game <srg 游戏 jar>] [--api <forge api 描述符清单>] [--out <报告>] <模组 jar...>}
 */
public final class ForgeApiScanner {

    private static final String FORGE = "net/minecraftforge/";

    /** 一个被引用的成员：类型 C/M/F + owner + name + desc。 */
    record Ref(char kind, String owner, String name, String desc) implements Comparable<Ref> {
        @Override
        public String toString() {
            return kind == 'T' ? owner : owner + "." + name + " " + desc;
        }

        @Override
        public int compareTo(Ref o) {
            return toString().compareTo(o.toString());
        }
    }

    /** 一个模组 jar 的扫描结果。 */
    static final class ModReport {
        final String jarName;
        final Set<Ref> forgeRefs = new TreeSet<>();
        final Set<String> forgeSupertypes = new TreeSet<>();
        final Set<String> subscribedEvents = new TreeSet<>();
        final Set<Ref> vanillaRefs = new TreeSet<>();
        final Set<String> mixinConfigs = new TreeSet<>();
        final Map<String, Set<String>> mixinTargets = new TreeMap<>();
        final List<String> atLines = new ArrayList<>();
        final List<String> nestedJars = new ArrayList<>();
        final Set<String> annotations = new TreeSet<>();
        final Set<String> ownClasses = new HashSet<>();
        int classCount;
        String modsToml;

        ModReport(String jarName) {
            this.jarName = jarName;
        }
    }

    private final Map<String, ClassNode> game = new HashMap<>();
    private final Set<String> knownApi = new HashSet<>();

    public static void main(String[] args) throws IOException {
        ForgeApiScanner s = new ForgeApiScanner();
        Path out = null;
        List<Path> jars = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--game" -> s.loadGame(Path.of(args[++i]));
                case "--api" -> s.loadApi(Path.of(args[++i]));
                case "--out" -> out = Path.of(args[++i]);
                default -> jars.add(Path.of(args[i]));
            }
        }
        List<ModReport> reports = new ArrayList<>();
        for (Path jar : jars) {
            reports.add(s.scan(jar));
        }
        if (out == null) {
            s.print(reports, System.out);
        } else {
            try (PrintStream ps = new PrintStream(Files.newOutputStream(out), true, StandardCharsets.UTF_8)) {
                s.print(reports, ps);
            }
        }
    }

    void loadGame(Path jar) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(jar))) {
            for (ZipEntry e; (e = zin.getNextEntry()) != null; ) {
                if (e.getName().endsWith(".class")) {
                    ClassNode cn = new ClassNode();
                    new ClassReader(zin.readAllBytes()).accept(cn, ClassReader.SKIP_CODE);
                    game.put(cn.name, cn);
                }
            }
        }
    }

    /** NeoForge 线生成的 forge-47.4.0-api.desc.txt：每行 “类型 owner name desc”。 */
    void loadApi(Path file) throws IOException {
        for (String line : Files.readAllLines(file)) {
            String[] p = line.split(" ");
            if (p.length >= 2) {
                knownApi.add(p[1]);
            }
            if (p.length >= 4) {
                knownApi.add(p[1] + "." + p[2] + " " + p[3]);
            }
        }
    }

    ModReport scan(Path jar) throws IOException {
        ModReport r = new ModReport(jar.getFileName().toString());
        scanJar(Files.readAllBytes(jar), r, "");
        return r;
    }

    private void scanJar(byte[] bytes, ModReport r, String prefix) throws IOException {
        List<ClassNode> classes = new ArrayList<>();
        Map<String, byte[]> nested = new TreeMap<>();
        try (JarInputStream jin = new JarInputStream(new ByteArrayInputStream(bytes))) {
            Manifest mf = jin.getManifest();
            if (mf != null && mf.getMainAttributes().getValue("MixinConfigs") != null) {
                for (String c : mf.getMainAttributes().getValue("MixinConfigs").split(",")) {
                    r.mixinConfigs.add(prefix + c.trim());
                }
            }
            for (ZipEntry e; (e = jin.getNextEntry()) != null; ) {
                String name = e.getName();
                if (name.endsWith(".class")) {
                    ClassNode cn = new ClassNode();
                    new ClassReader(readAll(jin)).accept(cn, 0);
                    classes.add(cn);
                } else if (name.equals("META-INF/accesstransformer.cfg")) {
                    for (String line : new String(readAll(jin), StandardCharsets.UTF_8).split("\n")) {
                        String t = line.strip();
                        if (!t.isEmpty() && !t.startsWith("#")) {
                            r.atLines.add(prefix + t);
                        }
                    }
                } else if (name.startsWith("META-INF/jarjar/") && name.endsWith(".jar")) {
                    nested.put(name, readAll(jin));
                } else if (name.equals("META-INF/mods.toml") && prefix.isEmpty()) {
                    r.modsToml = new String(readAll(jin), StandardCharsets.UTF_8);
                }
            }
        }
        for (ClassNode cn : classes) {
            r.ownClasses.add(cn.name);
        }
        r.classCount += classes.size();
        for (ClassNode cn : classes) {
            scanClass(cn, r);
        }
        for (Map.Entry<String, byte[]> e : nested.entrySet()) {
            r.nestedJars.add(prefix + e.getKey() + " (" + e.getValue().length + " bytes)");
            scanJar(e.getValue(), r, prefix + e.getKey() + "!/");
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        return in.readAllBytes();
    }

    private void scanClass(ClassNode cn, ModReport r) {
        if (cn.superName != null && cn.superName.startsWith(FORGE)) {
            r.forgeSupertypes.add(cn.superName);
        }
        for (String itf : cn.interfaces) {
            if (itf.startsWith(FORGE)) {
                r.forgeSupertypes.add(itf);
            }
        }
        boolean isMixin = false;
        for (AnnotationNode an : annotations(cn.visibleAnnotations, cn.invisibleAnnotations)) {
            noteAnnotation(an, r);
            if (an.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                isMixin = true;
                Set<String> targets = r.mixinTargets.computeIfAbsent(cn.name, k -> new TreeSet<>());
                for (Object v : valuesOf(an, "value")) {
                    if (v instanceof Type t) {
                        targets.add(t.getInternalName());
                    }
                }
                for (Object v : valuesOf(an, "targets")) {
                    targets.add(String.valueOf(v).replace('.', '/'));
                }
            }
        }
        for (FieldNode f : cn.fields) {
            noteDesc(f.desc, r);
            for (AnnotationNode an : annotations(f.visibleAnnotations, f.invisibleAnnotations)) {
                noteAnnotation(an, r);
            }
        }
        for (MethodNode m : cn.methods) {
            noteDesc(m.desc, r);
            for (AnnotationNode an : annotations(m.visibleAnnotations, m.invisibleAnnotations)) {
                noteAnnotation(an, r);
                if (an.desc.equals("Lnet/minecraftforge/eventbus/api/SubscribeEvent;")) {
                    Type[] params = Type.getArgumentTypes(m.desc);
                    if (params.length == 1) {
                        r.subscribedEvents.add(params[0].getInternalName());
                    }
                }
            }
            if (m.instructions == null) {
                continue;
            }
            for (var insn : m.instructions) {
                if (insn instanceof MethodInsnNode mi) {
                    noteMember('M', mi.owner, mi.name, mi.desc, r, isMixin);
                    if (mi.owner.equals("net/minecraftforge/eventbus/api/IEventBus") && mi.name.startsWith("addListener")) {
                        // addListener 的事件类型在前面的 invokedynamic 里：lambda 实现方法的最后一个参数
                        var prev = mi.getPrevious();
                        while (prev != null && !(prev instanceof InvokeDynamicInsnNode)) {
                            prev = prev.getPrevious();
                        }
                        if (prev instanceof InvokeDynamicInsnNode indy && indy.bsmArgs.length >= 3
                                && indy.bsmArgs[2] instanceof Type inst) {
                            Type[] p = inst.getArgumentTypes();
                            if (p.length == 1) {
                                r.subscribedEvents.add(p[0].getInternalName() + " (addListener)");
                            }
                        }
                    }
                } else if (insn instanceof FieldInsnNode fi) {
                    noteMember('F', fi.owner, fi.name, fi.desc, r, isMixin);
                } else if (insn instanceof TypeInsnNode ti) {
                    noteType(ti.desc, r);
                } else if (insn instanceof InvokeDynamicInsnNode indy) {
                    for (Object a : indy.bsmArgs) {
                        if (a instanceof Handle h) {
                            noteMember(h.getTag() <= Opcodes.H_PUTSTATIC ? 'F' : 'M', h.getOwner(), h.getName(),
                                    h.getDesc(), r, isMixin);
                        }
                    }
                } else if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Type t
                        && t.getSort() == Type.OBJECT) {
                    noteType(t.getInternalName(), r);
                }
            }
        }
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> a, List<AnnotationNode> b) {
        List<AnnotationNode> out = new ArrayList<>();
        if (a != null) {
            out.addAll(a);
        }
        if (b != null) {
            out.addAll(b);
        }
        return out;
    }

    private static List<Object> valuesOf(AnnotationNode an, String key) {
        List<Object> out = new ArrayList<>();
        if (an.values == null) {
            return out;
        }
        for (int i = 0; i + 1 < an.values.size(); i += 2) {
            if (key.equals(an.values.get(i))) {
                Object v = an.values.get(i + 1);
                if (v instanceof List<?> l) {
                    out.addAll(l);
                } else {
                    out.add(v);
                }
            }
        }
        return out;
    }

    private void noteAnnotation(AnnotationNode an, ModReport r) {
        String t = Type.getType(an.desc).getInternalName();
        if (t.startsWith(FORGE)) {
            r.annotations.add(t);
            r.forgeRefs.add(new Ref('T', t, "", ""));
        }
    }

    private void noteDesc(String desc, ModReport r) {
        Type t = Type.getType(desc);
        if (t.getSort() == Type.METHOD) {
            noteTypeObj(t.getReturnType(), r);
            for (Type a : t.getArgumentTypes()) {
                noteTypeObj(a, r);
            }
        } else {
            noteTypeObj(t, r);
        }
    }

    private void noteTypeObj(Type t, ModReport r) {
        while (t.getSort() == Type.ARRAY) {
            t = t.getElementType();
        }
        if (t.getSort() == Type.OBJECT) {
            noteType(t.getInternalName(), r);
        }
    }

    private void noteType(String internal, ModReport r) {
        if (internal.startsWith("[")) {
            noteDesc(internal, r);
            return;
        }
        if (internal.startsWith(FORGE)) {
            r.forgeRefs.add(new Ref('T', internal, "", ""));
        }
    }

    private void noteMember(char kind, String owner, String name, String desc, ModReport r, boolean inMixin) {
        if (owner.startsWith("[")) {
            return;
        }
        noteType(owner, r);
        noteDesc(desc, r);
        if (owner.startsWith(FORGE)) {
            r.forgeRefs.add(new Ref(kind, owner, name, desc));
            return;
        }
        if (game.isEmpty() || r.ownClasses.contains(owner)) {
            return;
        }
        if (owner.startsWith("net/minecraft/") || owner.startsWith("com/mojang/")) {
            if (!existsInGame(kind, owner, name, desc, new HashSet<>())) {
                r.vanillaRefs.add(new Ref(kind, owner, name, desc + (inMixin ? "  [in mixin]" : "")));
            }
        }
    }

    /** 在原版类和它的父类、接口里找这个成员。找不到类本身也算缺（例如 Forge 补丁新加的嵌套类）。 */
    private boolean existsInGame(char kind, String owner, String name, String desc, Set<String> seen) {
        ClassNode cn = game.get(owner);
        if (cn == null) {
            // 不在游戏 jar 里：JDK 类或库类，跳过
            return !owner.startsWith("net/minecraft/") || seen.size() > 0;
        }
        if (!seen.add(owner)) {
            return false;
        }
        if (kind == 'F') {
            for (FieldNode f : cn.fields) {
                if (f.name.equals(name) && f.desc.equals(desc)) {
                    return true;
                }
            }
        } else {
            for (MethodNode m : cn.methods) {
                if (m.name.equals(name) && m.desc.equals(desc)) {
                    return true;
                }
            }
        }
        if (cn.superName != null && existsInGame(kind, cn.superName, name, desc, seen)) {
            return true;
        }
        for (String i : cn.interfaces) {
            if (existsInGame(kind, i, name, desc, seen)) {
                return true;
            }
        }
        // 父类不在游戏 jar 里（例如 java/lang/Object、Enum）：方法可能来自那里
        return cn.superName != null && !game.containsKey(cn.superName) && kind == 'M'
                && (name.equals("values") || name.equals("valueOf") || name.equals("ordinal")
                || name.equals("name") || name.equals("toString") || name.equals("hashCode")
                || name.equals("equals") || name.equals("getClass") || name.equals("compareTo"));
    }

    void print(List<ModReport> reports, PrintStream out) {
        Map<String, Set<String>> forgeUsage = new TreeMap<>();
        Map<String, Set<String>> vanillaUsage = new TreeMap<>();
        Map<String, Set<String>> eventUsage = new TreeMap<>();
        for (ModReport r : reports) {
            out.println("# " + r.jarName);
            out.println();
            out.println("classes: " + r.classCount + ", forge refs: " + r.forgeRefs.size()
                    + ", missing vanilla members: " + r.vanillaRefs.size());
            section(out, "Forge supertypes", r.forgeSupertypes);
            section(out, "Forge annotations", r.annotations);
            section(out, "Subscribed events", r.subscribedEvents);
            List<String> refs = new ArrayList<>();
            for (Ref ref : r.forgeRefs) {
                String s = ref.toString();
                refs.add((knownApi.isEmpty() || knownApi.contains(s) || knownApi.contains(ref.owner()) ? "" : "[not in api list] ") + s);
                forgeUsage.computeIfAbsent(s, k -> new TreeSet<>()).add(r.jarName);
            }
            section(out, "Forge references", refs);
            List<String> van = new ArrayList<>();
            for (Ref ref : r.vanillaRefs) {
                van.add(ref.toString());
                vanillaUsage.computeIfAbsent(ref.toString(), k -> new TreeSet<>()).add(r.jarName);
            }
            for (String ev : r.subscribedEvents) {
                eventUsage.computeIfAbsent(ev.replace(" (addListener)", ""), k -> new TreeSet<>()).add(r.jarName);
            }
            section(out, "Vanilla members not in vanilla (Forge patches?)", van);
            section(out, "Mixin configs", r.mixinConfigs);
            List<String> mt = new ArrayList<>();
            r.mixinTargets.forEach((k, v) -> mt.add(k + " -> " + v));
            section(out, "Mixin classes -> targets", mt);
            section(out, "Access transformers", r.atLines);
            section(out, "Nested jars", r.nestedJars);
        }
        out.println("# Summary across " + reports.size() + " jars");
        out.println();
        List<String> ev = new ArrayList<>();
        eventUsage.forEach((k, v) -> ev.add(v.size() + "  " + k + "  " + v));
        section(out, "Events by number of mods", sortByCount(ev));
        List<String> fu = new ArrayList<>();
        forgeUsage.forEach((k, v) -> fu.add(v.size() + "  " + k));
        section(out, "Forge references by number of mods", sortByCount(fu));
        List<String> vu = new ArrayList<>();
        vanillaUsage.forEach((k, v) -> vu.add(v.size() + "  " + k + "  " + v));
        section(out, "Missing vanilla members by number of mods", sortByCount(vu));
    }

    private static List<String> sortByCount(List<String> l) {
        l.sort((a, b) -> {
            int ca = Integer.parseInt(a.substring(0, a.indexOf(' ')));
            int cb = Integer.parseInt(b.substring(0, b.indexOf(' ')));
            return ca != cb ? cb - ca : a.compareTo(b);
        });
        return l;
    }

    private static void section(PrintStream out, String title, java.util.Collection<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        out.println("## " + title + " (" + lines.size() + ")");
        for (String l : lines) {
            out.println("    " + l);
        }
        out.println();
    }
}
