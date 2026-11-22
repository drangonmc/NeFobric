package dev.nefobric.loader.mixin;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import dev.nefobric.loader.Env;
import dev.nefobric.loader.classloading.NeFobricClassLoader;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.spi.EcosystemAdapter;
import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 启动 Mixin：和 Fabric Loader 的 {@code FabricMixinBootstrap} + {@code finishMixinBootstrapping} 相同的步骤。
 * <ol>
 *   <li>指定 NeFobric 的 Mixin 服务，{@code MixinBootstrap.init()}，再初始化 MixinExtras；</li>
 *   <li>按模组加载顺序注册每个模组的 Mixin 配置，注册完让兼容层装饰（Fabric 记下模组 id 和兼容等级）；</li>
 *   <li>进入 INIT、DEFAULT 阶段，把 Mixin 变换器挂到类变换流水线的最后；</li>
 *   <li>先用一个空类触发一次配置选择，让 Mixin 插件的 {@code onLoad}、配置错误在移交游戏之前发生。</li>
 * </ol>
 */
public final class MixinSetup {
    /** 一个模组要注册的一个 Mixin 配置。 */
    public record ConfigRequest(String resource, ModCandidate mod, EcosystemAdapter adapter) {
    }

    private static final String WARMUP_CLASS = "nefobric.mixin.Warmup";
    private static boolean started;

    private MixinSetup() {
    }

    /**
     * @return 给玩家看的问题；为空表示 Mixin 已经就绪
     */
    public static synchronized List<String> start(NeFobricClassLoader classLoader, Env env,
                                                  List<ConfigRequest> requests) {
        if (started) {
            throw new IllegalStateException("Mixin is already started");
        }
        started = true;
        long t0 = System.nanoTime();
        MixinHost.set(classLoader, env);
        System.setProperty("mixin.bootstrapService", NeFobricMixinBootstrap.class.getName());
        System.setProperty("mixin.service", NeFobricMixinService.class.getName());
        MixinBootstrap.init();
        MixinExtrasBootstrap.init();

        List<String> problems = new ArrayList<>();
        Map<String, ConfigRequest> byName = new LinkedHashMap<>();
        Set<String> reported = new HashSet<>();
        for (ConfigRequest r : requests) {
            ConfigRequest prev = byName.putIfAbsent(r.resource(), r);
            if (prev != null) {
                if (prev.mod() != r.mod()) {
                    problems.add(Messages.tr("mixin.configConflict", r.resource(), prev.mod().describe(),
                            r.mod().describe()));
                }
                continue;
            }
            if (classLoader.getResource(r.resource()) == null) {
                problems.add(Messages.tr("mixin.configMissing", r.mod().describe(), r.resource()));
                reported.add(r.resource());
                continue;
            }
            try {
                Mixins.addConfiguration(r.resource());
            } catch (Throwable t) {
                Log.error("Cannot add Mixin config " + r.resource() + " of " + r.mod().describe(), t);
                problems.add(Messages.tr("mixin.configFailed", r.mod().describe(), r.resource(), t));
                reported.add(r.resource());
            }
        }

        Set<String> registered = new HashSet<>();
        for (Config config : Mixins.getConfigs()) {
            registered.add(config.getName());
            ConfigRequest r = byName.get(config.getName());
            if (r == null) {
                continue;
            }
            try {
                r.adapter().decorateMixinConfig(r.mod(), config.getName(), config.getConfig());
            } catch (RuntimeException e) {
                Log.error("Cannot decorate Mixin config " + config.getName(), e);
                problems.add(Messages.tr("mixin.configFailed", r.mod().describe(), config.getName(), e));
            }
        }
        for (ConfigRequest r : byName.values()) {
            // Mixin 读配置出错时只写日志，不抛异常：这里补一条给玩家看的
            if (!registered.contains(r.resource()) && reported.add(r.resource())) {
                problems.add(Messages.tr("mixin.configFailed", r.mod().describe(), r.resource(),
                        Messages.tr("mixin.seeLog")));
            }
        }
        if (!problems.isEmpty()) {
            return problems;
        }

        gotoPhase(MixinEnvironment.Phase.INIT);
        gotoPhase(MixinEnvironment.Phase.DEFAULT);
        IMixinTransformer transformer = NeFobricMixinService.transformer();
        if (transformer == null) {
            throw new IllegalStateException("Mixin did not provide a transformer to the NeFobric service");
        }
        classLoader.transformers().setMixin(
                (name, bytes) -> transformer.transformClassBytes(name, name, bytes),
                name -> transformer.generateClass(MixinEnvironment.getCurrentEnvironment(), name));
        try {
            transformer.transformClassBytes(WARMUP_CLASS, WARMUP_CLASS, warmupClass());
        } catch (Throwable t) {
            Log.error("Mixin failed while selecting configs", t);
            return List.of(Messages.tr("mixin.initFailed", t));
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        Log.info(Messages.tr("mixin.ready", MixinBootstrap.VERSION, registered.size(),
                byName.values().stream().map(ConfigRequest::mod).distinct().count(), ms));
        return List.of();
    }

    /** Mixin 只有这个包内可见的方法能切换阶段；Fabric Loader 也是这样用反射调用的。 */
    private static void gotoPhase(MixinEnvironment.Phase phase) {
        try {
            Method m = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
            m.setAccessible(true);
            m.invoke(null, phase);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot switch Mixin to phase " + phase, e);
        }
    }

    private static byte[] warmupClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC,
                WARMUP_CLASS.replace('.', '/'), null, "java/lang/Object", null);
        cw.visitEnd();
        return cw.toByteArray();
    }
}
