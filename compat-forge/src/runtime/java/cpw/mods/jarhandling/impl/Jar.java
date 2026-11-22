package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.SecureJar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * ModLauncher 里 {@link SecureJar} 的实现类。有模组按这个类型取清单（例如 ModernFix 压缩清单里的签名摘要），
 * 所以 NeFobric 给模组文件的也是它。NeFobric 不校验签名，{@link #getManifestSigners()} 总是 {@code null}。
 */
public class Jar implements SecureJar {
    private final Path primary;
    private final Supplier<Path> root;
    private final Supplier<String> name;
    private Manifest manifest;

    public Jar(Path primary, Supplier<Path> root, Supplier<String> name) {
        this.primary = primary;
        this.root = root;
        this.name = name;
    }

    @Override
    public Path getPrimaryPath() {
        return primary;
    }

    @Override
    public Path getRootPath() {
        return root.get();
    }

    @Override
    public String name() {
        return name.get();
    }

    /** jar 的清单；没有清单时是空的。读一次之后返回同一个对象。 */
    public synchronized Manifest getManifest() {
        if (manifest == null) {
            manifest = new Manifest();
            Path mf = getRootPath().resolve(JarFile.MANIFEST_NAME);
            if (Files.isRegularFile(mf)) {
                try (InputStream in = Files.newInputStream(mf)) {
                    manifest.read(in);
                } catch (IOException e) {
                    manifest = new Manifest();
                }
            }
        }
        return manifest;
    }

    @Override
    public CodeSigner[] getManifestSigners() {
        return null;
    }

    @Override
    public String toString() {
        return "Jar[" + primary + "]";
    }
}
