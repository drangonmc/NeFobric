package net.minecraftforge.fml.loading;

import java.nio.file.Path;

/** 文件名小工具。 */
public final class FileUtils {
    private FileUtils() {
    }

    /** 文件扩展名（不带点）；没有扩展名时是空串。 */
    public static String fileExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    /** 路径里的文件名是否包含任意一个关键字。 */
    public static boolean matchFileName(String path, String... matches) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = path.substring(slash + 1);
        for (String m : matches) {
            if (name.contains(m)) {
                return true;
            }
        }
        return false;
    }
}
