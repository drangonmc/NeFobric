package dev.nefobric.compat.fabric.apicheck;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 检查 NeFobric 提供的 Fabric Loader 类是否覆盖了真实世界的用法。两种模式：
 * <ul>
 *   <li>{@code --reference fabric-loader.jar}：逐项比对真实 Fabric Loader 的公开 API（类、方法、字段、static/default）；</li>
 *   <li>{@code --mods <目录或 jar>...}：扫描模组（含 Jar-in-Jar 内嵌的 jar）实际引用的 {@code net.fabricmc.loader.*}、
 *       {@code net.fabricmc.api.*} 类和成员，列出我们缺的。</li>
 * </ul>
 * 第一个参数是 NeFobric 兼容层编译好的类目录（或 jar）。
 */
public final class ApiSurfaceCheck {
    private static final String[] PREFIXES = {"net/fabricmc/loader/", "net/fabricmc/api/"};

    private final Map<String, ClassNode> ours = new TreeMap<>();

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: ApiSurfaceCheck <our classes dir|jar> (--reference <fabric-loader.jar> | --mods <dir|jar>...)");
            System.exit(2);
        }
        ApiSurfaceCheck check = new ApiSurfaceCheck();
        check.loadOurs(Paths.get(args[0]));
        int problems;
        if (args[1].equals("--reference")) {
            problems = check.compareWithReference(Paths.get(args[2]));
        } else {
            problems = 0;
            for (int i = 2; i < args.length; i++) {
                problems += check.scanMods(Paths.get(args[i]));
            }
        }
        System.out.println(problems == 0 ? "OK: nothing missing" : "MISSING: " + problems);
        System.exit(problems == 0 ? 0 : 1);
    }

    static boolean relevant(String internalName) {
        for (String p : PREFIXES) {
            if (internalName.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    // ---------- 读取 ----------

    void loadOurs(Path p) throws IOException {
        if (Files.isDirectory(p)) {
            try (Stream<Path> s = Files.walk(p)) {
                for (Path f : (Iterable<Path>) s::iterator) {
                    if (f.toString().endsWith(".class")) {
                        addOurs(Files.readAllBytes(f));
                    }
                }
            }
        } else {
            forEachClass(Files.readAllBytes(p), p.toString(), (name, bytes) -> addOurs(bytes));
        }
    }

    private void addOurs(byte[] bytes) {
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, ClassReader.SKIP_CODE);
        if (relevant(n.name)) {
            ours.put(n.name, n);
        }
    }

    interface ClassSink {
        void accept(String entryName, byte[] bytes) throws IOException;
    }

    /** 遍历 jar 里的类，包括 META-INF/jars 下内嵌的 jar（递归）。 */
    static void forEachClass(byte[] jar, String where, ClassSink sink) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                if (name.endsWith(".class") && !name.endsWith("module-info.class") && !name.startsWith("META-INF/versions/")) {
                    sink.accept(where + "!" + name, zin.readAllBytes());
                } else if (name.endsWith(".jar")) {
                    forEachClass(zin.readAllBytes(), where + "!" + name, sink);
                }
            }
        }
    }

    // ---------- 成员查找（沿父类和接口向上，JDK 类用反射）----------

    private boolean hasMethod(String owner, String name, String desc, Set<String> seen) {
        if (!seen.add(owner)) {
            return false;
        }
        ClassNode n = ours.get(owner);
        if (n != null) {
            for (MethodNode m : n.methods) {
                if (m.name.equals(name) && m.desc.equals(desc)) {
                    return true;
                }
            }
            if (n.superName != null && hasMethod(n.superName, name, desc, seen)) {
                return true;
            }
            for (String i : n.interfaces) {
                if (hasMethod(i, name, desc, seen)) {
                    return true;
                }
            }
            return false;
        }
        if (relevant(owner)) {
            return false;
        }
        return jdkHas(owner, name, desc, true);
    }

    private boolean hasField(String owner, String name, String desc, Set<String> seen) {
        if (!seen.add(owner)) {
            return false;
        }
        ClassNode n = ours.get(owner);
        if (n != null) {
            for (FieldNode f : n.fields) {
                if (f.name.equals(name) && f.desc.equals(desc)) {
                    return true;
                }
            }
            if (n.superName != null && hasField(n.superName, name, desc, seen)) {
                return true;
            }
            for (String i : n.interfaces) {
                if (hasField(i, name, desc, seen)) {
                    return true;
                }
            }
            return false;
        }
        if (relevant(owner)) {
            return false;
        }
        return jdkHas(owner, name, desc, false);
    }

    private static boolean jdkHas(String owner, String name, String desc, boolean method) {
        try {
            Class<?> c = Class.forName(owner.replace('/', '.'), false, ClassLoader.getPlatformClassLoader());
            for (Class<?> k = c; k != null; k = k.getSuperclass()) {
                if (jdkDeclares(k, name, desc, method)) {
                    return true;
                }
            }
            for (Class<?> i : allInterfaces(c)) {
                if (jdkDeclares(i, name, desc, method)) {
                    return true;
                }
            }
            return method && name.equals("<init>") && false;
        } catch (ClassNotFoundException e) {
            return true; // 不是 JDK 类也不是我们的，不归这里管
        }
    }

    private static Set<Class<?>> allInterfaces(Class<?> c) {
        Set<Class<?>> out = new java.util.LinkedHashSet<>();
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Class<?> i : k.getInterfaces()) {
                collect(i, out);
            }
        }
        return out;
    }

    private static void collect(Class<?> i, Set<Class<?>> out) {
        if (out.add(i)) {
            for (Class<?> s : i.getInterfaces()) {
                collect(s, out);
            }
        }
    }

    private static boolean jdkDeclares(Class<?> k, String name, String desc, boolean method) {
        if (method) {
            if (name.equals("<init>")) {
                for (var ctor : k.getDeclaredConstructors()) {
                    if (Type.getConstructorDescriptor(ctor).equals(desc)) {
                        return true;
                    }
                }
                return false;
            }
            for (var m : k.getDeclaredMethods()) {
                if (m.getName().equals(name) && Type.getMethodDescriptor(m).equals(desc)) {
                    return true;
                }
            }
        } else {
            for (var f : k.getDeclaredFields()) {
                if (f.getName().equals(name) && Type.getDescriptor(f.getType()).equals(desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------- 模式一：与真实 Fabric Loader 比对 ----------

    int compareWithReference(Path refJar) throws IOException {
        Set<String> missing = new TreeSet<>();
        forEachClass(Files.readAllBytes(refJar), refJar.toString(), (entry, bytes) -> {
            ClassNode ref = new ClassNode();
            new ClassReader(bytes).accept(ref, ClassReader.SKIP_CODE);
            // 只比对公开 API 包；impl 包里被模组直接用到的类由 --mods 模式发现
            if (!(ref.name.startsWith("net/fabricmc/api/") || ref.name.startsWith("net/fabricmc/loader/api/"))) {
                return;
            }
            if ((ref.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0 && !ref.name.contains("$")) {
                return;
            }
            ClassNode our = ours.get(ref.name);
            if (our == null) {
                missing.add("class " + ref.name);
                return;
            }
            if (!java.util.Objects.equals(ref.superName, our.superName)) {
                missing.add("superclass " + ref.name + ": " + ref.superName + " (ours " + our.superName + ")");
            }
            for (String i : ref.interfaces) {
                if (!our.interfaces.contains(i)) {
                    missing.add("interface " + ref.name + " implements " + i);
                }
            }
            int kindMask = Opcodes.ACC_INTERFACE | Opcodes.ACC_ENUM | Opcodes.ACC_ANNOTATION | Opcodes.ACC_ABSTRACT | Opcodes.ACC_FINAL;
            if ((ref.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ENUM | Opcodes.ACC_ANNOTATION)) != (our.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ENUM | Opcodes.ACC_ANNOTATION))) {
                missing.add("kind " + ref.name + ": access " + Integer.toHexString(ref.access & kindMask) + " (ours " + Integer.toHexString(our.access & kindMask) + ")");
            }
            for (MethodNode m : ref.methods) {
                if ((m.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0 || (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                    continue;
                }
                MethodNode o = null;
                for (MethodNode x : our.methods) {
                    if (x.name.equals(m.name) && x.desc.equals(m.desc)) {
                        o = x;
                    }
                }
                if (o == null) {
                    missing.add("method " + ref.name + "." + m.name + m.desc);
                    continue;
                }
                int mask = Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT;
                if ((m.access & mask) != (o.access & mask)) {
                    missing.add("modifiers " + ref.name + "." + m.name + m.desc + ": " + Integer.toHexString(m.access & mask)
                            + " (ours " + Integer.toHexString(o.access & mask) + ")");
                }
                if (m.signature != null && !m.signature.equals(o.signature)) {
                    missing.add("generic signature " + ref.name + "." + m.name + ": " + m.signature + " (ours " + o.signature + ")");
                }
            }
            for (FieldNode f : ref.fields) {
                if ((f.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0 || (f.access & Opcodes.ACC_SYNTHETIC) != 0) {
                    continue;
                }
                boolean found = our.fields.stream().anyMatch(x -> x.name.equals(f.name) && x.desc.equals(f.desc));
                if (!found) {
                    missing.add("field " + ref.name + "." + f.name + " " + f.desc);
                }
                if (f.value != null) {
                    our.fields.stream().filter(x -> x.name.equals(f.name)).findFirst().ifPresent(x -> {
                        if (!f.value.equals(x.value)) {
                            missing.add("constant " + ref.name + "." + f.name + " = " + f.value + " (ours " + x.value + ")");
                        }
                    });
                }
            }
        });
        missing.forEach(System.out::println);
        return missing.size();
    }

    // ---------- 模式二：模组实际引用了什么 ----------

    int scanMods(Path p) throws IOException {
        java.util.List<Path> jars = new ArrayList<>();
        if (Files.isDirectory(p)) {
            try (Stream<Path> s = Files.walk(p)) {
                s.filter(f -> f.toString().endsWith(".jar")).sorted().forEach(jars::add);
            }
        } else {
            jars.add(p);
        }
        Map<String, Set<String>> missing = new TreeMap<>();
        for (Path jar : jars) {
            forEachClass(Files.readAllBytes(jar), jar.getFileName().toString(), (entry, bytes) -> {
                String modName = entry.substring(0, entry.lastIndexOf('!'));
                new ClassReader(bytes).accept(new RefCollector(ref -> {
                    if (!resolves(ref)) {
                        missing.computeIfAbsent(ref, k -> new TreeSet<>()).add(modName);
                    }
                }), 0);
            });
        }
        missing.forEach((ref, users) -> System.out.println(ref + "    <- " + String.join(", ", users)));
        return missing.size();
    }

    private boolean resolves(String ref) {
        String[] parts = ref.split(" ");
        switch (parts[0]) {
            case "class":
                return ours.containsKey(parts[1]);
            case "method":
                return hasMethod(parts[1], parts[2], parts[3], new java.util.HashSet<>());
            case "field":
                return hasField(parts[1], parts[2], parts[3], new java.util.HashSet<>());
            default:
                return true;
        }
    }

    /** 收集一个类里对 Fabric Loader 类和成员的全部引用，格式为 “class X”、“method X name desc”、“field X name desc”。 */
    static final class RefCollector extends ClassVisitor {
        private final java.util.function.Consumer<String> out;

        RefCollector(java.util.function.Consumer<String> out) {
            super(Opcodes.ASM9);
            this.out = out;
        }

        void type(Type t) {
            if (t == null) {
                return;
            }
            switch (t.getSort()) {
                case Type.ARRAY:
                    type(t.getElementType());
                    break;
                case Type.OBJECT:
                    if (relevant(t.getInternalName())) {
                        out.accept("class " + t.getInternalName());
                    }
                    break;
                case Type.METHOD:
                    type(t.getReturnType());
                    for (Type a : t.getArgumentTypes()) {
                        type(a);
                    }
                    break;
                default:
                    break;
            }
        }

        void internal(String name) {
            if (name != null) {
                type(name.startsWith("[") ? Type.getType(name) : Type.getObjectType(name));
            }
        }

        void desc(String d) {
            if (d != null) {
                type(Type.getType(d));
            }
        }

        void constant(Object v) {
            if (v instanceof Type t) {
                type(t);
            } else if (v instanceof Handle h) {
                handle(h);
            }
        }

        void handle(Handle h) {
            internal(h.getOwner());
            if (relevant(h.getOwner())) {
                boolean field = h.getTag() <= Opcodes.H_PUTSTATIC;
                out.accept((field ? "field " : "method ") + h.getOwner() + " " + h.getName() + " " + h.getDesc());
            }
            desc(h.getDesc());
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            internal(superName);
            if (interfaces != null) {
                for (String i : interfaces) {
                    internal(i);
                }
            }
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            desc(descriptor);
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            desc(descriptor);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            desc(descriptor);
            if (exceptions != null) {
                for (String e : exceptions) {
                    internal(e);
                }
            }
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitTypeInsn(int opcode, String type) {
                    internal(type);
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String n, String d) {
                    internal(owner);
                    desc(d);
                    if (relevant(owner)) {
                        out.accept("field " + owner + " " + n + " " + d);
                    }
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String n, String d, boolean itf) {
                    internal(owner);
                    desc(d);
                    if (relevant(owner)) {
                        out.accept("method " + owner + " " + n + " " + d);
                    }
                }

                @Override
                public void visitInvokeDynamicInsn(String n, String d, Handle bsm, Object... bsmArgs) {
                    desc(d);
                    handle(bsm);
                    for (Object a : bsmArgs) {
                        constant(a);
                    }
                }

                @Override
                public void visitLdcInsn(Object value) {
                    constant(value);
                }

                @Override
                public void visitMultiANewArrayInsn(String d, int dims) {
                    desc(d);
                }

                @Override
                public void visitTryCatchBlock(org.objectweb.asm.Label start, org.objectweb.asm.Label end,
                                               org.objectweb.asm.Label handler, String type) {
                    internal(type);
                }

                @Override
                public void visitLocalVariable(String n, String d, String sig, org.objectweb.asm.Label s,
                                               org.objectweb.asm.Label e, int index) {
                    desc(d);
                }

                @Override
                public org.objectweb.asm.AnnotationVisitor visitAnnotation(String d, boolean visible) {
                    desc(d);
                    return null;
                }
            };
        }
    }

    @SuppressWarnings("unused")
    private static byte[] read(InputStream in) throws IOException {
        return in.readAllBytes();
    }
}
