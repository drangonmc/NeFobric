package net.minecraftforge.forgespi.language;

import java.util.List;
import java.util.Optional;

/** mods.toml 里的一个表（或整个文件），按路径取值。 */
public interface IConfigurable {
    <T> Optional<T> getConfigElement(String... key);

    List<? extends IConfigurable> getConfigList(String... key);
}
