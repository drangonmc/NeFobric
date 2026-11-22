package net.fabricmc.loader.impl.launch;

import net.fabricmc.api.EnvType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.jar.Manifest;

/**
 * Fabric Loader 的内部接口。它不是公开 API，但不少模组直接用它，最常见的是 fabric-language-kotlin 的
 * {@code FabricLauncherBase.getLauncher().getTargetClassLoader()}，所以按 Fabric Loader 0.16 的签名提供。
 */
public interface FabricLauncher {
    MappingConfiguration getMappingConfiguration();

    void addToClassPath(Path path, String... allowedPrefixes);

    void setAllowedPrefixes(Path path, String... prefixes);

    void setValidParentClassPath(Collection<Path> paths);

    EnvType getEnvironmentType();

    boolean isClassLoaded(String name);

    Class<?> loadIntoTarget(String name) throws ClassNotFoundException;

    InputStream getResourceAsStream(String name);

    ClassLoader getTargetClassLoader();

    byte[] getClassByteArray(String name, boolean runTransformers) throws IOException;

    Manifest getManifest(Path originPath);

    boolean isDevelopment();

    String getEntrypoint();

    String getTargetNamespace();

    List<Path> getClassPath();
}
