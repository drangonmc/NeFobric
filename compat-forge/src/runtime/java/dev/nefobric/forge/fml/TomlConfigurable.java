package dev.nefobric.forge.fml;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraftforge.forgespi.language.IConfigurable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** 把 night-config 读出来的 TOML 表包装成 Forge 的 {@link IConfigurable}。 */
public final class TomlConfigurable implements IConfigurable {
    private final UnmodifiableConfig config;

    public TomlConfigurable(UnmodifiableConfig config) {
        this.config = config;
    }

    public UnmodifiableConfig config() {
        return config;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getConfigElement(String... key) {
        if (config == null) {
            return Optional.empty();
        }
        Object v = config.get(Arrays.asList(key));
        if (v instanceof UnmodifiableConfig c) {
            return Optional.of((T) c.valueMap());
        }
        return Optional.ofNullable((T) v);
    }

    @Override
    public List<? extends IConfigurable> getConfigList(String... key) {
        if (config == null) {
            return Collections.emptyList();
        }
        Object v = config.get(Arrays.asList(key));
        if (!(v instanceof List<?> l)) {
            return Collections.emptyList();
        }
        List<IConfigurable> out = new ArrayList<>();
        for (Object o : l) {
            if (o instanceof UnmodifiableConfig c) {
                out.add(new TomlConfigurable(c));
            }
        }
        return out;
    }
}
