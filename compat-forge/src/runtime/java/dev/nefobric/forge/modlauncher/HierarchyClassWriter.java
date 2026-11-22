package dev.nefobric.forge.modlauncher;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 重算栈帧时找公共父类：读类文件里的继承关系，不加载类（类加载过程中再去加载别的类容易出问题）。
 * 找不到的类按 {@code java/lang/Object} 处理。
 */
public class HierarchyClassWriter extends ClassWriter {
    private static final Map<String, Info> CACHE = new ConcurrentHashMap<>();
    private static final Info MISSING = new Info(null, new String[0], false);

    private final ClassLoader loader;

    public HierarchyClassWriter(int flags, ClassLoader loader) {
        super(flags);
        this.loader = loader;
    }

    private record Info(String superName, String[] interfaces, boolean isInterface) {
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        if (type1.equals(type2)) {
            return type1;
        }
        if (isAssignable(type1, type2)) {
            return type1;
        }
        if (isAssignable(type2, type1)) {
            return type2;
        }
        Info a = info(type1);
        Info b = info(type2);
        if (a == MISSING || b == MISSING || a.isInterface() || b.isInterface()) {
            return "java/lang/Object";
        }
        String c = type1;
        do {
            c = info(c).superName();
            if (c == null) {
                return "java/lang/Object";
            }
        } while (!isAssignable(c, type2));
        return c;
    }

    /** {@code sub} 是不是 {@code sup} 本身或它的子类型。 */
    private boolean isAssignable(String sup, String sub) {
        if (sup.equals("java/lang/Object")) {
            return true;
        }
        Deque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(sub);
        while (!queue.isEmpty()) {
            String c = queue.poll();
            if (!seen.add(c)) {
                continue;
            }
            if (c.equals(sup)) {
                return true;
            }
            Info i = info(c);
            if (i.superName() != null) {
                queue.add(i.superName());
            }
            for (String itf : i.interfaces()) {
                queue.add(itf);
            }
        }
        return false;
    }

    private Info info(String name) {
        Info cached = CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        Info info = read(name);
        CACHE.put(name, info);
        return info;
    }

    private Info read(String name) {
        ClassLoader cl = loader != null ? loader : ClassLoader.getSystemClassLoader();
        try (InputStream in = cl.getResourceAsStream(name + ".class")) {
            if (in == null) {
                return MISSING;
            }
            ClassReader r = new ClassReader(in);
            return new Info(r.getSuperName(), r.getInterfaces(), (r.getAccess() & Opcodes.ACC_INTERFACE) != 0);
        } catch (IOException | RuntimeException e) {
            return MISSING;
        }
    }
}
