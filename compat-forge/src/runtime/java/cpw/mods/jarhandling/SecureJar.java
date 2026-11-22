package cpw.mods.jarhandling;

import java.nio.file.Path;
import java.security.CodeSigner;

/**
 * ModLauncher 的 jar 抽象。Forge 的 {@code IModFile.getSecureJar()} 返回它；NeFobric 只提供模组会用到的部分。
 */
public interface SecureJar {
    Path getPrimaryPath();

    Path getRootPath();

    default Path getPath(String first, String... rest) {
        Path p = getRootPath().resolve(first.startsWith("/") ? first.substring(1) : first);
        for (String r : rest) {
            p = p.resolve(r);
        }
        return p;
    }

    String name();

    default boolean hasSecurityData() {
        return false;
    }

    /** 清单的签名者；NeFobric 不校验签名，没有。 */
    default CodeSigner[] getManifestSigners() {
        return null;
    }

    enum Status {
        NONE, INVALID, UNVERIFIED, VERIFIED
    }
}
