package net.fabricmc.loader.api.metadata;

import java.nio.file.Path;
import java.util.List;

/** 模组从哪里来：普通文件，或者内嵌在另一个模组里。 */
public interface ModOrigin {
    Kind getKind();

    /** 只对 {@link Kind#PATH} 有效。 */
    List<Path> getPaths();

    /** 只对 {@link Kind#NESTED} 有效。 */
    String getParentModId();

    /** 只对 {@link Kind#NESTED} 有效：在父模组里的位置，例如 {@code META-INF/jars/x.jar}。 */
    String getParentSubLocation();

    enum Kind {
        PATH,
        NESTED,
        UNKNOWN
    }
}
