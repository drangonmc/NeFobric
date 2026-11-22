package dev.nefobric.compat.forge.devtools.remap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 类的继承关系（来源命名空间），重映射时沿父类和接口找成员是在哪儿声明的。 */
public final class ClassIndex {
    record Info(String superName, String[] interfaces) {
    }

    private final Map<String, Info> classes = new HashMap<>();
    private final Map<String, List<String>> ancestors = new HashMap<>();

    public void addJar(Path jar) throws IOException {
        try (InputStream in = Files.newInputStream(jar); ZipInputStream zin = new ZipInputStream(in)) {
            for (ZipEntry e; (e = zin.getNextEntry()) != null; ) {
                if (e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/")) {
                    addClass(zin.readAllBytes());
                }
            }
        }
    }

    public void addClass(byte[] bytes) {
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                              String[] interfaces) {
                classes.putIfAbsent(name, new Info(superName, interfaces == null ? new String[0] : interfaces));
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    /** 自己、所有父类和接口（广度优先，先父类后接口），每个只出现一次。不认识的类到此为止。 */
    public synchronized List<String> ancestors(String name) {
        List<String> cached = ancestors.get(name);
        if (cached != null) {
            return cached;
        }
        Set<String> out = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(name);
        Set<String> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            String c = queue.poll();
            if (!seen.add(c)) {
                continue;
            }
            out.add(c);
            Info info = classes.get(c);
            if (info == null) {
                continue;
            }
            if (info.superName != null) {
                queue.add(info.superName);
            }
            for (String itf : info.interfaces) {
                queue.add(itf);
            }
        }
        List<String> list = new ArrayList<>(out);
        ancestors.put(name, list);
        return list;
    }

    public int size() {
        return classes.size();
    }
}
