package net.minecraftforge.forgespi.locating;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/** 找到模组 jar 的来源。NeFobric 自己发现模组，这个接口只为签名兼容。 */
public interface IModProvider {
    String name();

    void scanFile(IModFile modFile, Consumer<Path> pathConsumer);

    void initArguments(Map<String, ?> arguments);

    boolean isValid(IModFile modFile);
}
