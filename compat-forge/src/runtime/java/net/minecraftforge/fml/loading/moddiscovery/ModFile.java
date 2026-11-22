package net.minecraftforge.fml.loading.moddiscovery;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.impl.Jar;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.IModLanguageProvider;
import net.minecraftforge.forgespi.language.ModFileScanData;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModProvider;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** 一个 Forge 系模组 jar。资源通过 jar 的 zip 文件系统读取。 */
public class ModFile implements IModFile {
    private final Path filePath;
    private final String jarVersion;
    private ModFileInfo modFileInfo;
    private ModFileScanData scanData;
    private FileSystem fileSystem;
    private Map<String, Object> fileProperties = Map.of();
    private final List<Path> accessTransformers = new ArrayList<>();

    public ModFile(Path filePath, String jarVersion) {
        this.filePath = filePath;
        this.jarVersion = jarVersion;
    }

    public void setModFileInfo(ModFileInfo info) {
        this.modFileInfo = info;
    }

    public synchronized void setScanResult(ModFileScanData data, Throwable throwable) {
        this.scanData = data;
    }

    @Override
    public Supplier<Map<String, Object>> getSubstitutionMap() {
        return () -> Map.of("file", Map.of("jarVersion", jarVersion == null ? "0.0NONE" : jarVersion));
    }

    @Override
    public Type getType() {
        return Type.MOD;
    }

    @Override
    public Path getFilePath() {
        return filePath;
    }

    @Override
    public SecureJar getSecureJar() {
        return new Jar(filePath, this::root,
                () -> modFileInfo == null ? filePath.getFileName().toString() : modFileInfo.moduleName());
    }

    @Override
    public void setSecurityStatus(SecureJar.Status status) {
    }

    @Override
    public List<IModInfo> getModInfos() {
        return modFileInfo == null ? List.of() : modFileInfo.getMods();
    }

    public Optional<Path> getAccessTransformer() {
        Path p = findResource("META-INF", "accesstransformer.cfg");
        return Files.exists(p) ? Optional.of(p) : Optional.empty();
    }

    public List<Path> getAccessTransformers() {
        return getAccessTransformer().map(List::of).orElse(List.of());
    }

    @Override
    public synchronized ModFileScanData getScanResult() {
        if (scanData == null) {
            // Forge 在后台线程里提前扫描；NeFobric 第一次用到时再扫
            scanData = dev.nefobric.forge.fml.ModScanner.scan(this);
        }
        return scanData;
    }

    public void setFileProperties(Map<String, Object> fileProperties) {
        this.fileProperties = fileProperties;
    }

    @Override
    public List<IModLanguageProvider> getLoaders() {
        return List.of();
    }

    synchronized Path root() {
        if (fileSystem == null) {
            try {
                if (Files.isDirectory(filePath)) {
                    return filePath;
                }
                URI uri = URI.create("jar:" + filePath.toUri());
                try {
                    fileSystem = FileSystems.newFileSystem(uri, Map.of());
                } catch (FileSystemAlreadyExistsException e) {
                    fileSystem = FileSystems.getFileSystem(uri);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return fileSystem.getPath("/");
    }

    @Override
    public Path findResource(String... path) {
        if (path.length == 1 && path[0].contains("/")) {
            path = path[0].split("/");
        }
        Path p = root();
        for (String s : path) {
            if (!s.isEmpty()) {
                p = p.resolve(s);
            }
        }
        return p;
    }

    @Override
    public String toString() {
        return "Mod File: " + filePath;
    }

    @Override
    public String getFileName() {
        return filePath.getFileName().toString();
    }

    @Override
    public IModProvider getProvider() {
        return null;
    }

    @Override
    public IModFileInfo getModFileInfo() {
        return modFileInfo;
    }

    public ArtifactVersion getJarVersion() {
        return new DefaultArtifactVersion(jarVersion == null ? "0.0NONE" : jarVersion);
    }
}
