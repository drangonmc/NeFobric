package dev.nefobric.compat.fabric.impl;

import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.util.Log;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 一个模组（任何生态）的 Fabric 视图。 */
public final class ModContainerImpl implements ModContainer {
    private static final Map<Path, Path> JAR_ROOTS = new ConcurrentHashMap<>();

    private final ModCandidate candidate;
    private final ModMetadata metadata;
    private final List<Path> codeSources;
    private final ModOrigin origin;
    private volatile List<Path> rootPaths;
    private ModContainer parent;
    private final List<ModContainer> children = new ArrayList<>();

    public ModContainerImpl(ModCandidate candidate, ModMetadata metadata, List<Path> codeSources, ModOrigin origin) {
        this.candidate = candidate;
        this.metadata = metadata;
        this.codeSources = List.copyOf(codeSources);
        this.origin = origin;
    }

    void setParent(ModContainer parent) {
        this.parent = parent;
    }

    void addChild(ModContainer child) {
        children.add(child);
    }

    public ModCandidate candidate() {
        return candidate;
    }

    /** 模组运行时所在的 jar 或目录。 */
    public List<Path> codeSources() {
        return codeSources;
    }

    @Override
    public ModMetadata getMetadata() {
        return metadata;
    }

    @Override
    public List<Path> getRootPaths() {
        List<Path> ret = rootPaths;
        if (ret == null) {
            synchronized (this) {
                ret = rootPaths;
                if (ret == null) {
                    List<Path> list = new ArrayList<>(codeSources.size());
                    for (Path p : codeSources) {
                        list.add(rootOf(p));
                    }
                    rootPaths = ret = Collections.unmodifiableList(list);
                }
            }
        }
        return ret;
    }

    /** jar 打开成 zip 文件系统并返回根目录；目录原样返回。同一个 jar 只打开一次，之后一直不关。 */
    static Path rootOf(Path codeSource) {
        if (Files.isDirectory(codeSource)) {
            return codeSource;
        }
        return JAR_ROOTS.computeIfAbsent(codeSource.toAbsolutePath().normalize(), jar -> {
            try {
                FileSystem fs;
                try {
                    fs = FileSystems.newFileSystem(jar, (ClassLoader) null);
                } catch (FileSystemAlreadyExistsException e) {
                    fs = FileSystems.getFileSystem(URI.create("jar:" + jar.toUri()));
                }
                return fs.getRootDirectories().iterator().next();
            } catch (IOException | FileSystemNotFoundException e) {
                throw new UncheckedIOException(new IOException("Cannot open " + jar + " as a zip file system", e));
            }
        });
    }

    @Override
    public ModOrigin getOrigin() {
        return origin;
    }

    @Override
    public Optional<ModContainer> getContainingMod() {
        return Optional.ofNullable(parent);
    }

    @Override
    public Collection<ModContainer> getContainedMods() {
        return Collections.unmodifiableList(children);
    }

    @Override
    @Deprecated
    public Path getRootPath() {
        List<Path> paths = getRootPaths();
        if (paths.size() != 1) {
            Log.warn("getRootPath() called on mod " + metadata.getId() + " with " + paths.size()
                    + " root paths, use getRootPaths() instead");
        }
        if (paths.isEmpty()) {
            throw new IllegalStateException("Mod " + metadata.getId() + " has no root path");
        }
        return paths.get(0);
    }

    @Override
    public String toString() {
        return metadata.getId() + " " + metadata.getVersion().getFriendlyString();
    }
}
