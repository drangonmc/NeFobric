package dev.nefobric.compat.fabric.impl;

import dev.nefobric.loader.Env;
import dev.nefobric.loader.classloading.NeFobricClassLoader;
import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.launch.GameLocator;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/** {@link FabricLauncherBase} 的唯一实例，背后是 NeFobric 的游戏类加载器。 */
public final class NeFobricFabricLauncher extends FabricLauncherBase {
    public static final NeFobricFabricLauncher INSTANCE = new NeFobricFabricLauncher();

    private final Map<String, Object> properties = new ConcurrentHashMap<>();

    private NeFobricFabricLauncher() {
    }

    public Map<String, Object> properties() {
        return properties;
    }

    private static NeFobricClassLoader loader() {
        return NeFobricLoader.get().classLoader();
    }

    @Override
    public void addToClassPath(Path path, String... allowedPrefixes) {
        loader().addJar(path);
    }

    @Override
    public void setAllowedPrefixes(Path path, String... prefixes) {
        // NeFobric 的类加载器不按路径限制包前缀
    }

    @Override
    public void setValidParentClassPath(Collection<Path> paths) {
        // 同上
    }

    @Override
    public EnvType getEnvironmentType() {
        return NeFobricLoader.get().env() == Env.CLIENT ? EnvType.CLIENT : EnvType.SERVER;
    }

    @Override
    public boolean isClassLoaded(String name) {
        return NeFobricLoader.isInitialized() && loader().isClassLoaded(name);
    }

    @Override
    public Class<?> loadIntoTarget(String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader());
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return loader().getResourceAsStream(name);
    }

    @Override
    public ClassLoader getTargetClassLoader() {
        return NeFobricLoader.isInitialized() ? loader() : Thread.currentThread().getContextClassLoader();
    }

    /**
     * 与 Knot 相同：{@code runTransformers} 时返回 Mixin 之前各阶段（入口钩子、@Environment 剥离、AW）处理过的字节，
     * 否则返回类文件原样的字节。找不到时返回 {@code null}。
     */
    @Override
    public byte[] getClassByteArray(String name, boolean runTransformers) throws IOException {
        return runTransformers ? loader().getPreMixinClassBytes(name) : loader().getRawClassBytes(name);
    }

    @Override
    public Manifest getManifest(Path originPath) {
        try {
            if (Files.isDirectory(originPath)) {
                Path mf = originPath.resolve("META-INF/MANIFEST.MF");
                if (!Files.isRegularFile(mf)) {
                    return null;
                }
                try (InputStream in = Files.newInputStream(mf)) {
                    return new Manifest(in);
                }
            }
            try (JarFile jar = new JarFile(originPath.toFile())) {
                return jar.getManifest();
            }
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean isDevelopment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public String getEntrypoint() {
        return NeFobricLoader.get().env() == Env.CLIENT ? GameLocator.CLIENT_MAIN : GameLocator.SERVER_MAIN;
    }

    @Override
    public String getTargetNamespace() {
        return CoreBridge.runtimeNamespace();
    }

    @Override
    public List<Path> getClassPath() {
        List<Path> out = new ArrayList<>();
        for (URL u : loader().getURLs()) {
            try {
                out.add(Paths.get(u.toURI()));
            } catch (URISyntaxException | IllegalArgumentException e) {
                // 不是文件路径的条目跳过
            }
        }
        return out;
    }
}
