package dev.nefobric.compat.fabric;

import dev.nefobric.loader.Env;
import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** {@link FabricLoader} 的实现，数据来自 NeFobric 的统一元数据。 */
public final class FabricLoaderShim implements FabricLoader {
    public static final FabricLoaderShim INSTANCE = new FabricLoaderShim();

    final FabricEntrypoints entrypoints = new FabricEntrypoints();

    private FabricLoaderShim() {
    }

    private static NeFobricLoader loader() {
        return NeFobricLoader.get();
    }

    @Override
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        NeFobricLoader loader = loader();
        List<T> out = new ArrayList<>();
        for (ModCandidate mod : loader.mods()) {
            if (mod.ecosystem() != Ecosystem.FABRIC) {
                continue;
            }
            for (String spec : mod.entrypoints(key)) {
                try {
                    out.add(entrypoints.create(mod, spec, type, loader.classLoader()));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    throw new IllegalStateException("Could not create entrypoint " + spec + " of " + mod.id(), e);
                }
            }
        }
        return out;
    }

    /** Fabric 模组也能看到 Forge 系模组和内置模组（minecraft、java、forge 等），这是统一加载器的意义所在。 */
    @Override
    public boolean isModLoaded(String id) {
        return loader().isModLoaded(id);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return loader().isDevelopmentEnvironment();
    }

    @Override
    public EnvType getEnvironmentType() {
        return loader().env() == Env.CLIENT ? EnvType.CLIENT : EnvType.SERVER;
    }

    @Override
    public Path getGameDir() {
        return loader().gameDir();
    }

    @Override
    @Deprecated
    public File getGameDirectory() {
        return getGameDir().toFile();
    }

    @Override
    public Path getConfigDir() {
        return loader().configDir();
    }

    @Override
    @Deprecated
    public File getConfigDirectory() {
        return getConfigDir().toFile();
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        String[] args = loader().launchArguments();
        if (!sanitize) {
            return args;
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--accessToken") || args[i].equals("--uuid") || args[i].equals("--xuid")
                    || args[i].equals("--clientId")) {
                i++; // 去掉账号相关的参数
                continue;
            }
            out.add(args[i]);
        }
        return out.toArray(new String[0]);
    }
}
