package dev.nefobric.forge.fml;

import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.spi.ModLoadingException;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.LoadingFailedException;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModLoadingWarning;
import net.minecraftforge.fml.ModWorkManager;
import net.minecraftforge.fml.Logging;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * NeFobric 调用 Forge 运行时的入口（ForgeAdapter 通过反射调用，因为运行时由游戏类加载器加载）。
 *
 * <p>和 Forge 一样分三段：{@link #gather()}（构造模组、注册表）在游戏钩子里；{@link #load()}（配置、setup）和
 * {@link #complete()}（IMC、加载完成、冻结）在客户端第一次资源重载时、在服务端紧接着 gather。原版钩子还没装上时
 * （测试用的假游戏、或者 Forge 的原版补丁没有生效）由 {@link #runRemainingIfNoGameHooks()} 直接跑完。</p>
 */
public final class ForgeRuntime {
    private static final Logger LOGGER = LogManager.getLogger("NeFobric/Forge");
    private static volatile boolean gameHooksInstalled;
    private static volatile boolean gathered;
    private static volatile boolean loaded;
    private static volatile boolean completed;

    private ForgeRuntime() {
    }

    /** 原版补丁里的资源重载钩子会接管 LOAD/COMPLETE 时调用。 */
    public static void markGameHooksInstalled() {
        gameHooksInstalled = true;
    }

    public static boolean gameHooksInstalled() {
        return gameHooksInstalled;
    }

    public static synchronized void gather() throws ModLoadingException {
        if (gathered) {
            return;
        }
        gathered = true;
        LOGGER.info(Logging.LOADING, "Forge {} on NeFobric: loading {} mods", ForgeEnvironment.forgeVersion(),
                ForgeEnvironment.forgeMods().size());
        MinecraftForge.initialize();
        run(() -> ModLoader.get().gatherAndInitializeMods(ModWorkManager.syncExecutor(),
                ModWorkManager.parallelExecutor(), () -> {
                }), "construct");
    }

    public static synchronized void load() throws ModLoadingException {
        if (loaded || !gathered) {
            return;
        }
        loaded = true;
        run(() -> ModLoader.get().loadMods(ModWorkManager.syncExecutor(), ModWorkManager.parallelExecutor(), () -> {
        }), "setup");
    }

    public static synchronized void complete() throws ModLoadingException {
        if (completed || !loaded) {
            return;
        }
        completed = true;
        run(() -> ModLoader.get().finishMods(ModWorkManager.syncExecutor(), ModWorkManager.parallelExecutor(), () -> {
        }), "load complete");
        List<ModLoadingWarning> warnings = ModLoader.get().getWarnings();
        if (!warnings.isEmpty()) {
            LOGGER.warn(Logging.LOADING, "Mods loaded with {} warnings", warnings.size());
            warnings.forEach(w -> LOGGER.warn(Logging.LOADING, w.formatToString()));
        }
        MinecraftForge.EVENT_BUS.start();
    }

    /** 没有原版钩子接管时，在游戏钩子里把 LOAD 和 COMPLETE 也跑完。 */
    public static void runRemainingIfNoGameHooks() throws ModLoadingException {
        if (!gameHooksInstalled) {
            load();
            complete();
        }
    }

    public static boolean isLoadingComplete() {
        return completed;
    }

    private static void run(Runnable step, String what) throws ModLoadingException {
        try {
            step.run();
        } catch (LoadingFailedException e) {
            IModInfo info = e.getErrors().stream().map(net.minecraftforge.fml.ModLoadingException::getModInfo)
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);
            String message = e.getErrors().isEmpty() ? e.getMessage()
                    : e.getErrors().get(0).getCleanMessage();
            Throwable cause = e.getErrors().isEmpty() ? e
                    : Optional.ofNullable(e.getErrors().get(0).getCause()).orElse(e);
            throw new ModLoadingException(candidateFor(info), "Forge " + what + ": " + message, cause);
        } catch (RuntimeException | Error e) {
            throw new ModLoadingException(candidateFor(null), "Forge " + what, e);
        }
    }

    private static ModCandidate candidateFor(IModInfo info) {
        if (info != null) {
            for (ModCandidate m : ForgeEnvironment.forgeMods()) {
                if (m.id().equals(info.getModId())) {
                    return m;
                }
            }
        }
        if (NeFobricLoader.isInitialized()) {
            Optional<ModCandidate> forge = NeFobricLoader.get().mod("forge");
            if (forge.isPresent()) {
                return forge.get();
            }
        }
        return ModCandidate.builder("forge", ForgeEnvironment.forgeVersion(), Ecosystem.BUILTIN).build();
    }
}
