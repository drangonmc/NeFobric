package dev.nefobric.loader.mapping;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把原版 jar 从 {@value Mappings#OFFICIAL} 整体重映射到运行时命名空间。官方名很短而且不唯一（{@code a}、{@code b}），
 * 所以成员要沿继承链查找：子类引用父类声明的方法时，映射条目写在父类上。
 *
 * <p>和 Forge 的 ART 一样会把 {@code SourceFile} 改成外部类名加 {@code .java}，崩溃报告里的堆栈因此可读。
 */
public final class GameJarRemapper {
    private static final String MISSING = "\0";

    private final MappingData mappings;
    private final int from;
    private final int to;
    private final Map<String, ClassInfo> hierarchy = new HashMap<>();
    private final Map<String, String> memberCache = new ConcurrentHashMap<>();

    record ClassInfo(String name, String superName, String[] interfaces, Map<String, Integer> methods,
                     Map<String, Integer> fields) {
    }

    public GameJarRemapper(MappingData mappings, String fromNs, String toNs) {
        this.mappings = mappings;
        this.from = mappings.namespaceIndex(fromNs);
        this.to = mappings.namespaceIndex(toNs);
    }

    /** 重映射 {@code in}，写到 {@code out}。返回处理的类数。 */
    public int remap(Path in, Path out) throws IOException {
        List<JarIO.Entry> entries = JarIO.read(in);
        for (JarIO.Entry e : entries) {
            if (e.isClass()) {
                ClassInfo info = scan(e.data());
                hierarchy.put(info.name(), info);
            }
        }
        Remapper remapper = new HierarchyRemapper();
        Map<String, byte[]> result = new LinkedHashMap<>();
        int classes = 0;
        for (JarIO.Entry e : entries) {
            if (e.isClass()) {
                ClassReader reader = new ClassReader(e.data());
                ClassWriter writer = new ClassWriter(0);
                reader.accept(new ClassRemapper(new SourceFileFix(writer, remapper.map(reader.getClassName())),
                        remapper), 0);
                String newName = remapper.map(reader.getClassName());
                result.put(newName + ".class", writer.toByteArray());
                classes++;
            } else if (!JarIO.isSignatureFile(e.name())) {
                result.put(e.name(), e.data());
            }
        }
        JarIO.write(out, result);
        return classes;
    }

    private static ClassInfo scan(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        Map<String, Integer> methods = new HashMap<>();
        Map<String, Integer> fields = new HashMap<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature,
                                           Object value) {
                fields.put(name, access);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                methods.put(name + descriptor, access);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ClassInfo(reader.getClassName(), reader.getSuperName(), reader.getInterfaces(), methods, fields);
    }

    /** 方法沿继承链找映射；父类的私有方法不算（它们不会被重写）。 */
    String resolveMethod(String owner, String name, String desc) {
        String key = owner + '.' + name + desc;
        String cached = memberCache.get(key);
        if (cached != null) {
            return cached == MISSING ? null : cached;
        }
        String r = resolveMethod(owner, name, desc, true, 0);
        memberCache.put(key, r == null ? MISSING : r);
        return r;
    }

    private String resolveMethod(String owner, String name, String desc, boolean self, int depth) {
        if (depth > 64) {
            return null;
        }
        ClassInfo info = hierarchy.get(owner);
        String official = from == 0 ? owner : mappings.toOfficial(from, owner);
        if (official != null) {
            String[] names = mappings.methodNames(official, name, from == 0 ? desc : mappings.mapDesc(from, 0, desc));
            if (names != null) {
                Integer access = info == null ? null : info.methods().get(name + desc);
                if (self || access == null || (access & Opcodes.ACC_PRIVATE) == 0) {
                    String n = names[to];
                    return n != null ? n : name;
                }
            }
        }
        if (info == null) {
            return null;
        }
        if (info.superName() != null) {
            String r = resolveMethod(info.superName(), name, desc, false, depth + 1);
            if (r != null) {
                return r;
            }
        }
        for (String itf : info.interfaces()) {
            String r = resolveMethod(itf, name, desc, false, depth + 1);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    String resolveField(String owner, String name, int depth) {
        if (depth > 64) {
            return null;
        }
        ClassInfo info = hierarchy.get(owner);
        String official = from == 0 ? owner : mappings.toOfficial(from, owner);
        if (official != null && (info == null || info.fields().containsKey(name) || depth == 0)) {
            String[] names = mappings.fieldNames(official, name);
            if (names != null) {
                String n = names[to];
                return n != null ? n : name;
            }
        }
        if (info == null) {
            return null;
        }
        for (String itf : info.interfaces()) {
            String r = resolveField(itf, name, depth + 1);
            if (r != null) {
                return r;
            }
        }
        return info.superName() == null ? null : resolveField(info.superName(), name, depth + 1);
    }

    /** invokedynamic 只给出接口和方法名，没有方法描述符：在接口里按名字找，所有候选映射一致时才换名。 */
    String resolveMethodByName(String owner, String name, int depth) {
        if (depth > 64) {
            return null;
        }
        ClassInfo info = hierarchy.get(owner);
        if (info == null) {
            return null;
        }
        String found = null;
        for (String m : info.methods().keySet()) {
            if (m.startsWith(name + "(")) {
                String r = resolveMethod(owner, name, m.substring(name.length()));
                if (r != null) {
                    if (found != null && !found.equals(r)) {
                        return null;
                    }
                    found = r;
                }
            }
        }
        if (found != null) {
            return found;
        }
        for (String itf : info.interfaces()) {
            String r = resolveMethodByName(itf, name, depth + 1);
            if (r != null) {
                return r;
            }
        }
        return info.superName() == null ? null : resolveMethodByName(info.superName(), name, depth + 1);
    }

    private final class HierarchyRemapper extends Remapper {
        @Override
        public String map(String internalName) {
            return mappings.mapClassInternal(from, to, internalName);
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            if (name.startsWith("<") || owner.startsWith("[")) {
                return name;
            }
            String r = resolveMethod(owner, name, descriptor);
            return r == null ? name : r;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            String key = owner + ':' + name;
            String cached = memberCache.get(key);
            if (cached == null) {
                String r = resolveField(owner, name, 0);
                cached = r == null ? MISSING : r;
                memberCache.put(key, cached);
            }
            return cached == MISSING ? name : cached;
        }

        @Override
        public String mapRecordComponentName(String owner, String name, String descriptor) {
            return mapFieldName(owner, name, descriptor);
        }

        @Override
        public String mapInvokeDynamicMethodName(String name, String descriptor) {
            String ret = descriptor.substring(descriptor.indexOf(')') + 1);
            if (!ret.startsWith("L")) {
                return name;
            }
            String r = resolveMethodByName(ret.substring(1, ret.length() - 1), name, 0);
            return r == null ? name : r;
        }

        @Override
        public String mapAnnotationAttributeName(String descriptor, String name) {
            if (!descriptor.startsWith("L")) {
                return name;
            }
            String r = resolveMethodByName(descriptor.substring(1, descriptor.length() - 1), name, 0);
            return r == null ? name : r;
        }
    }

    /** 和 Forge 的 ART 一样：SourceFile 写成外部类的简单名加 .java。 */
    static final class SourceFileFix extends ClassVisitor {
        private final String sourceFile;

        SourceFileFix(ClassVisitor next, String remappedName) {
            super(Opcodes.ASM9, next);
            String simple = remappedName.substring(remappedName.lastIndexOf('/') + 1);
            int dollar = simple.indexOf('$');
            if (dollar > 0) {
                simple = simple.substring(0, dollar);
            }
            this.sourceFile = simple + ".java";
        }

        @Override
        public void visitSource(String source, String debug) {
            super.visitSource(sourceFile, debug);
        }
    }
}
