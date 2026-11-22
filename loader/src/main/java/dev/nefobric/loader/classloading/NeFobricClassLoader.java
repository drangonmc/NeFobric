package dev.nefobric.loader.classloading;

import dev.nefobric.loader.transform.TransformPipeline;
import dev.nefobric.loader.transform.TransformerPhase;
import dev.nefobric.loader.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Manifest;

/**
 * 游戏和所有模组共用的平铺类加载器。
 *
 * <p>游戏 jar、兼容层运行库和模组 jar 里的类由它自己定义（子优先），这样每个类在定义前都会经过
 * {@link TransformPipeline}（入口钩子、按端剥离、AW/AT、Mixin）。JDK、游戏依赖库（LWJGL、netty 等）以及加载器自己（{@code dev.nefobric.loader/compat/shadow} 和兼容层提供的 API 类）
 * 交给父加载器，保证加载器和模组看到的是同一份 API 类。
 */
public final class NeFobricClassLoader extends URLClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    /**
     * 加载器自己的包（含重定位后的依赖库）。注意不是整个 dev.nefobric，NeFobric 的测试模组也在这个命名空间下。
     * ASM、Mixin 和 MixinExtras 也由加载器统一提供：Mixin 的公开接口直接用 ASM 的类型，模组自带（JiJ）的
     * MixinExtras 如果被子优先加载出第二份就会和加载器初始化的那份对不上。
     */
    private static final List<String> ALWAYS_PARENT = List.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "dev.nefobric.loader.", "dev.nefobric.compat.", "dev.nefobric.shadow.",
            "org.objectweb.asm.", "org.spongepowered.", "com.llamalad7.mixinextras.");

    private final List<String> parentPackages = new CopyOnWriteArrayList<>();
    private final List<String> parentClasses = new CopyOnWriteArrayList<>();
    private final TransformPipeline pipeline = new TransformPipeline();
    private final Map<String, CodeSource> codeSources = new ConcurrentHashMap<>();
    private final AtomicInteger definedCount = new AtomicInteger();

    public NeFobricClassLoader(List<Path> jars, ClassLoader parent, Collection<String> loaderOwned) {
        super("NeFobric", toUrls(jars), parent);
        parentPackages.addAll(ALWAYS_PARENT);
        for (String s : loaderOwned) {
            if (s.endsWith(".")) {
                parentPackages.add(s);
            } else {
                parentClasses.add(s);
            }
        }
    }

    private static URL[] toUrls(List<Path> jars) {
        URL[] urls = new URL[jars.size()];
        for (int i = 0; i < urls.length; i++) {
            try {
                urls[i] = jars.get(i).toUri().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Bad path " + jars.get(i), e);
            }
        }
        return urls;
    }

    /** 等同于 {@code transformers().add(TransformerPhase.GAME_PATCH, transformer)}。 */
    public void addTransformer(ClassTransformer transformer) {
        pipeline.add(TransformerPhase.GAME_PATCH, transformer);
    }

    /** 这个类加载器的类变换流水线。 */
    public TransformPipeline transformers() {
        return pipeline;
    }

    /** 这个类加载器是否已经定义过这个类（Mixin 用它提示“目标类在 Mixin 就绪之前就被加载了”）。 */
    public boolean isClassLoaded(String name) {
        return findLoadedClass(name) != null;
    }

    /**
     * 类文件的原始字节：游戏、运行库和模组里的类从这里的 jar 读，其余（JDK、游戏依赖库、加载器自己的类）从父加载器读。
     * 找不到时返回 {@code null}。
     */
    public byte[] getRawClassBytes(String name) throws IOException {
        URL url = locate(name);
        if (url == null) {
            return null;
        }
        try (InputStream in = url.openStream()) {
            return in.readAllBytes();
        }
    }

    /**
     * Mixin 之前各阶段（入口钩子、按端剥离、AW/AT）处理过的字节码，Mixin 读类信息时用它。
     * 不归这里定义的类不做变换。找不到时返回 {@code null}。
     */
    public byte[] getPreMixinClassBytes(String name) throws IOException {
        URL own = isLoaderOwned(name) ? null : findResource(classFile(name));
        if (own == null) {
            return getRawClassBytes(name);
        }
        byte[] bytes;
        try (InputStream in = own.openStream()) {
            bytes = in.readAllBytes();
        }
        return pipeline.transformBeforeMixin(name, bytes);
    }

    private URL locate(String name) {
        String path = classFile(name);
        URL url = isLoaderOwned(name) ? null : findResource(path);
        return url != null ? url : getParent().getResource(path);
    }

    private static String classFile(String name) {
        return name.replace('.', '/') + ".class";
    }

    /** 之后（例如 Jar-in-Jar）再加入的 jar。 */
    public void addJar(Path jar) {
        try {
            addURL(jar.toUri().toURL());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Bad path " + jar, e);
        }
    }

    public int definedClassCount() {
        return definedCount.get();
    }

    boolean isLoaderOwned(String name) {
        for (String p : parentPackages) {
            if (name.startsWith(p)) {
                return true;
            }
        }
        for (String c : parentClasses) {
            if (name.equals(c) || name.startsWith(c + "$")) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null && !isLoaderOwned(name)) {
                c = defineFromOwnJars(name);
            }
            if (c == null) {
                try {
                    c = getParent().loadClass(name);
                } catch (ClassNotFoundException e) {
                    // javax.* 不全在 JDK 里（例如 jsr305 的 javax.annotation），父加载器没有时再看模组 jar
                    c = name.startsWith("javax.") ? defineFromOwnJars(name) : null;
                    if (c == null) {
                        // Mixin 运行时生成的合成类（例如 org.spongepowered.asm.synthetic.args.Args$1）哪里都没有
                        c = defineGenerated(name);
                    }
                    if (c == null) {
                        throw e;
                    }
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> c = isLoaderOwned(name) ? null : defineFromOwnJars(name);
        if (c == null) {
            c = defineGenerated(name);
        }
        if (c == null) {
            throw new ClassNotFoundException(name);
        }
        return c;
    }

    private Class<?> defineFromOwnJars(String name) throws ClassNotFoundException {
        URL url = findResource(classFile(name));
        if (url == null) {
            return null;
        }
        byte[] bytes;
        Manifest manifest = null;
        try {
            URLConnection conn = url.openConnection();
            if (conn instanceof JarURLConnection jar) {
                manifest = jar.getManifest();
            }
            try (InputStream in = conn.getInputStream()) {
                bytes = in.readAllBytes();
            }
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
        bytes = pipeline.transform(name, bytes);
        CodeSource cs = codeSourceFor(url);
        definePackageFor(name, manifest, cs.getLocation());
        Class<?> c = defineClass(name, bytes, 0, bytes.length, cs);
        definedCount.incrementAndGet();
        return c;
    }

    private Class<?> defineGenerated(String name) {
        byte[] bytes = pipeline.generate(name);
        if (bytes == null) {
            return null;
        }
        definePackageFor(name, null, null);
        Class<?> c = defineClass(name, bytes, 0, bytes.length);
        definedCount.incrementAndGet();
        return c;
    }

    private void definePackageFor(String className, Manifest manifest, URL location) {
        int dot = className.lastIndexOf('.');
        if (dot <= 0) {
            return;
        }
        String pkg = className.substring(0, dot);
        if (getDefinedPackage(pkg) != null) {
            return;
        }
        try {
            if (manifest != null) {
                definePackage(pkg, manifest, location);
            } else {
                definePackage(pkg, null, null, null, null, null, null, null);
            }
        } catch (IllegalArgumentException alreadyDefined) {
            // 并发定义时别的线程已经定义了这个包
        }
    }

    /** 由类文件的 URL 得到所在 jar 的 CodeSource（jar:file:/x.jar!/a/B.class → file:/x.jar）。 */
    private CodeSource codeSourceFor(URL classUrl) {
        String s = classUrl.toString();
        String root;
        if (s.startsWith("jar:")) {
            int sep = s.indexOf("!/");
            root = sep > 0 ? s.substring(4, sep) : s;
        } else {
            root = s;
            for (URL u : getURLs()) {
                String us = u.toString();
                if (s.startsWith(us)) {
                    root = us;
                    break;
                }
            }
        }
        return codeSources.computeIfAbsent(root, r -> {
            try {
                return new CodeSource(new URL(r), (CodeSigner[]) null);
            } catch (MalformedURLException e) {
                return new CodeSource(classUrl, (CodeSigner[]) null);
            }
        });
    }

    /** 资源也子优先：模组可以覆盖同名资源，模组自己的资源一定能找到。 */
    @Override
    public URL getResource(String name) {
        URL url = findResource(name);
        return url != null ? url : getParent().getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Map<String, URL> seen = new LinkedHashMap<>();
        for (Enumeration<URL> e = findResources(name); e.hasMoreElements(); ) {
            URL u = e.nextElement();
            seen.putIfAbsent(u.toString(), u);
        }
        for (Enumeration<URL> e = getParent().getResources(name); e.hasMoreElements(); ) {
            URL u = e.nextElement();
            seen.putIfAbsent(u.toString(), u);
        }
        return Collections.enumeration(new ArrayList<>(seen.values()));
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        URL url = getResource(name);
        try {
            return url == null ? null : url.openStream();
        } catch (IOException e) {
            Log.debug("Cannot open resource " + name + ": " + e);
            return null;
        }
    }
}
