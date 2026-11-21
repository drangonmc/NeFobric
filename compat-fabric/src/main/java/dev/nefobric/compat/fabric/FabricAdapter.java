package dev.nefobric.compat.fabric;

import dev.nefobric.loader.Env;
import dev.nefobric.loader.discovery.ModJar;
import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.launch.GameInstall;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.ModMetadataException;
import dev.nefobric.loader.spi.EcosystemAdapter;
import dev.nefobric.loader.spi.LoadStage;
import dev.nefobric.loader.spi.ModLoadingException;
import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** Fabric 兼容层：读 fabric.mod.json，按 Fabric 的顺序调用入口。 */
public final class FabricAdapter implements EcosystemAdapter {
    /**
     * 对外声明的 Fabric Loader 版本，只是为了让模组的 {@code "fabricloader": ">=0.14.x"} 依赖检查通过，
     * 并不代表已经实现了这个版本的全部 API。
     */
    public static final String EMULATED_FABRIC_LOADER_VERSION = "0.16.10";

    static final String METADATA = "fabric.mod.json";

    @Override
    public String displayName() {
        return "Fabric";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean hasMetadata(ModJar jar) {
        return jar.has(METADATA);
    }

    @Override
    public List<ModCandidate> readMods(ModJar jar) throws ModMetadataException {
        try {
            String json = jar.readString(METADATA).orElseThrow();
            return List.of(FabricMetadataParser.parse(json, jar.path()));
        } catch (IOException e) {
            throw new ModMetadataException(e.toString(), e);
        }
    }

    @Override
    public List<ModCandidate> builtinMods(GameInstall game) {
        return List.of(ModCandidate.builder("fabricloader", EMULATED_FABRIC_LOADER_VERSION, Ecosystem.BUILTIN)
                .name("Fabric Loader (NeFobric)")
                .build());
    }

    @Override
    public Collection<String> loaderOwnedClasses() {
        return List.of("net.fabricmc.api.", "net.fabricmc.loader.api.");
    }

    @Override
    public boolean owns(ModCandidate mod) {
        return mod.ecosystem() == Ecosystem.FABRIC;
    }

    @Override
    public List<String> warningsFor(ModCandidate mod) {
        List<String> out = new ArrayList<>();
        if (!mod.mixinConfigs().isEmpty()) {
            out.add(Messages.tr("compat.mixinsIgnored", mod.describe(), String.join(", ", mod.mixinConfigs())));
        }
        if (!mod.accessTransformers().isEmpty()) {
            out.add(Messages.tr("fabric.awIgnored", mod.describe(), String.join(", ", mod.accessTransformers())));
        }
        if (!mod.nestedJars().isEmpty()) {
            out.add(Messages.tr("compat.nestedIgnored", mod.describe(), mod.nestedJars().size()));
        }
        return out;
    }

    @Override
    public void runStage(LoadStage stage, NeFobricLoader loader, List<ModCandidate> mods) throws ModLoadingException {
        switch (stage) {
            case PRE_LAUNCH:
                invoke(loader, mods, "preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);
                break;
            case MAIN:
                invoke(loader, mods, "main", ModInitializer.class, ModInitializer::onInitialize);
                break;
            case SIDE:
                if (loader.env() == Env.CLIENT) {
                    invoke(loader, mods, "client", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
                } else {
                    invoke(loader, mods, "server", DedicatedServerModInitializer.class,
                            DedicatedServerModInitializer::onInitializeServer);
                }
                break;
            default:
                break;
        }
    }

    private static <T> void invoke(NeFobricLoader loader, List<ModCandidate> mods, String key, Class<T> type,
                                   Consumer<T> call) throws ModLoadingException {
        FabricEntrypoints entrypoints = FabricLoaderShim.INSTANCE.entrypoints;
        for (ModCandidate mod : mods) {
            for (String spec : mod.entrypoints(key)) {
                Log.debug("Fabric " + key + ": " + mod.id() + " -> " + spec);
                try {
                    call.accept(entrypoints.create(mod, spec, type, loader.classLoader()));
                } catch (Throwable t) {
                    throw new ModLoadingException(mod, key + " (" + spec + ")", unwrap(t));
                }
            }
        }
    }

    private static Throwable unwrap(Throwable t) {
        if (t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null) {
            return ite.getCause();
        }
        if (t instanceof ExceptionInInitializerError eie && eie.getCause() != null) {
            return eie.getCause();
        }
        return t;
    }
}
