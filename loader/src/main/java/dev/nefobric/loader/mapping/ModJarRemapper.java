package dev.nefobric.loader.mapping;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把模组 jar 从 {@value Mappings#INTERMEDIARY} 重映射到运行时命名空间。intermediary 的名字全局唯一
 * （{@code net/minecraft/class_310}、{@code method_1234}、{@code field_5678}），所以不需要继承关系：
 * <ul>
 *   <li>字节码里的类名按类映射换，成员名只要是唯一名就直接换；</li>
 *   <li>字符串常量和注解里的字符串（Mixin 的 {@code @At(target=...)}、{@code @Mixin(targets=...)}、
 *       {@code @Accessor("...")} 等，以及模组用来反射的类名）里出现的唯一名也换掉；</li>
 *   <li>Mixin 的 refmap（含 {@code "mappings"} 的 JSON）里的唯一名同样换掉。</li>
 * </ul>
 * 嵌套 jar 原样保留，由 Jar-in-Jar 解包后各自重映射。
 */
public final class ModJarRemapper {
    private static final Pattern CLASS_SLASH = Pattern.compile("net/minecraft/class_\\d+(?:\\$[A-Za-z0-9_]+)*");
    private static final Pattern CLASS_DOT = Pattern.compile("net\\.minecraft\\.class_\\d+(?:\\$[A-Za-z0-9_]+)*");
    private static final Pattern MEMBER = Pattern.compile("\\b(?:method|field|comp)_\\d+\\b(?=(\\(?))");

    private final MappingData mappings;
    private final int from;
    private final int to;

    public ModJarRemapper(MappingData mappings, String fromNs, String toNs) {
        this.mappings = mappings;
        this.from = mappings.namespaceIndex(fromNs);
        this.to = mappings.namespaceIndex(toNs);
    }

    /** 重映射 {@code in}，写到 {@code out}。返回处理的类数。 */
    public int remap(Path in, Path out) throws IOException {
        List<JarIO.Entry> entries = JarIO.read(in);
        Remapper remapper = new UniqueNameRemapper();
        Map<String, byte[]> result = new LinkedHashMap<>();
        int classes = 0;
        for (JarIO.Entry e : entries) {
            if (e.isClass()) {
                ClassReader reader = new ClassReader(e.data());
                ClassWriter writer = new ClassWriter(0);
                reader.accept(new ClassRemapper(writer, remapper), 0);
                result.put(remapper.map(reader.getClassName()) + ".class", writer.toByteArray());
                classes++;
            } else if (JarIO.isSignatureFile(e.name())) {
                // 重映射后签名必然失效
            } else if (isRefmap(e)) {
                result.put(e.name(), remapString(new String(e.data(), StandardCharsets.UTF_8))
                        .getBytes(StandardCharsets.UTF_8));
            } else {
                result.put(e.name(), e.data());
            }
        }
        JarIO.write(out, result);
        return classes;
    }

    private static boolean isRefmap(JarIO.Entry e) {
        if (!e.name().endsWith(".json") || e.data().length > 16 * 1024 * 1024) {
            return false;
        }
        String head = new String(e.data(), 0, Math.min(e.data().length, 4096), StandardCharsets.UTF_8);
        return head.stripLeading().startsWith("{") && head.contains("\"mappings\"");
    }

    /** 替换字符串里所有 intermediary 唯一名（类名两种写法和成员名）。 */
    public String remapString(String s) {
        if (s.indexOf("class_") < 0 && s.indexOf("method_") < 0 && s.indexOf("field_") < 0
                && s.indexOf("comp_") < 0) {
            return s;
        }
        String r = replace(CLASS_SLASH, s, m -> mappings.mapClassInternal(from, to, m.group()));
        r = replace(CLASS_DOT, r, m -> mappings.mapClassInternal(from, to, m.group().replace('.', '/'))
                .replace('/', '.'));
        r = replace(MEMBER, r, m -> {
            String token = m.group();
            String next = m.group(1);
            String mapped;
            if (token.startsWith("field_")) {
                mapped = mappings.mapUniqueField(from, to, token);
            } else if (token.startsWith("method_")) {
                mapped = mappings.mapUniqueMethod(from, to, token);
            } else {
                // 记录组件：后面跟着 ( 是访问方法，否则按字段
                mapped = "(".equals(next) ? mappings.mapUniqueMethod(from, to, token)
                        : mappings.mapUniqueField(from, to, token);
                if (mapped == null) {
                    mapped = "(".equals(next) ? mappings.mapUniqueField(from, to, token)
                            : mappings.mapUniqueMethod(from, to, token);
                }
            }
            return mapped == null ? token : mapped;
        });
        return r;
    }

    private interface Replacer {
        String apply(Matcher m);
    }

    private static String replace(Pattern p, String s, Replacer f) {
        Matcher m = p.matcher(s);
        if (!m.find()) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length() + 32);
        int last = 0;
        do {
            sb.append(s, last, m.start()).append(f.apply(m));
            last = m.end();
        } while (m.find());
        sb.append(s, last, s.length());
        return sb.toString();
    }

    private final class UniqueNameRemapper extends Remapper {
        @Override
        public String map(String internalName) {
            return mappings.mapClassInternal(from, to, internalName);
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            String r = mappings.mapUniqueMethod(from, to, name);
            return r == null ? name : r;
        }

        @Override
        public String mapInvokeDynamicMethodName(String name, String descriptor) {
            String r = mappings.mapUniqueMethod(from, to, name);
            return r == null ? name : r;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            String r = mappings.mapUniqueField(from, to, name);
            return r == null ? name : r;
        }

        @Override
        public String mapRecordComponentName(String owner, String name, String descriptor) {
            return mapFieldName(owner, name, descriptor);
        }

        @Override
        public String mapAnnotationAttributeName(String descriptor, String name) {
            String r = mappings.mapUniqueMethod(from, to, name);
            return r == null ? name : r;
        }

        @Override
        public Object mapValue(Object value) {
            if (value instanceof String s) {
                return remapString(s);
            }
            return super.mapValue(value);
        }
    }
}
