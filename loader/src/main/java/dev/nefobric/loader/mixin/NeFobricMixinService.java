package dev.nefobric.loader.mixin;

import dev.nefobric.loader.Env;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.util.Constants;
import org.spongepowered.asm.util.ReEntranceLock;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NeFobric 的 Mixin 宿主服务，对应 Fabric Loader 的 {@code MixinServiceKnot}、Forge 的 {@code MixinServiceModLauncher}。
 *
 * <p>类信息从 {@link dev.nefobric.loader.classloading.NeFobricClassLoader} 读：游戏和模组的类给的是 Mixin 之前各阶段
 * （入口钩子、按端剥离、AW/AT）处理过的字节码，和 Fabric 一样。Mixin 自己的变换器由
 * {@link #offer(IMixinInternal)} 收到，再由 {@link MixinSetup} 挂到流水线的最后。
 */
public final class NeFobricMixinService implements IMixinService, IClassProvider, IClassBytecodeProvider,
        ITransformerProvider, IClassTracker {
    private static volatile IMixinTransformer transformer;
    private static final Map<String, ILogger> LOGGERS = new ConcurrentHashMap<>();

    private final ReEntranceLock lock = new ReEntranceLock(1);

    static IMixinTransformer transformer() {
        return transformer;
    }

    // ---- IMixinService ----

    @Override
    public String getName() {
        return "NeFobric";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void prepare() {
    }

    @Override
    public MixinEnvironment.Phase getInitialPhase() {
        return MixinEnvironment.Phase.PREINIT;
    }

    @Override
    public void offer(IMixinInternal internal) {
        if (internal instanceof IMixinTransformerFactory factory) {
            transformer = factory.createTransformer();
        }
    }

    @Override
    public void init() {
    }

    @Override
    public void beginPhase() {
    }

    @Override
    public void checkEnv(Object bootSource) {
    }

    @Override
    public ReEntranceLock getReEntranceLock() {
        return lock;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return this;
    }

    @Override
    public IClassTracker getClassTracker() {
        return this;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return List.of("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault");
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        return new ContainerHandleVirtual(getName());
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        return List.of();
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return MixinHost.classLoader().getResourceAsStream(name);
    }

    @Override
    public String getSideName() {
        Env env = MixinHost.env();
        if (env == null) {
            return Constants.SIDE_UNKNOWN;
        }
        return env == Env.CLIENT ? Constants.SIDE_CLIENT : Constants.SIDE_SERVER;
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_8;
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_22;
    }

    @Override
    public ILogger getLogger(String name) {
        return LOGGERS.computeIfAbsent(name, MixinLogger::new);
    }

    // ---- IClassProvider ----

    @Override
    @Deprecated
    public URL[] getClassPath() {
        return new URL[0];
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return MixinHost.classLoader().loadClass(name);
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, MixinHost.classLoader());
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, NeFobricMixinService.class.getClassLoader());
    }

    // ---- IClassBytecodeProvider ----

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return getClassNode(name, true);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        return getClassNode(name, runTransformers, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags)
            throws ClassNotFoundException, IOException {
        String binaryName = name.replace('/', '.');
        byte[] bytes = runTransformers ? MixinHost.classLoader().getPreMixinClassBytes(binaryName)
                : MixinHost.classLoader().getRawClassBytes(binaryName);
        if (bytes == null) {
            throw new ClassNotFoundException(binaryName);
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, readerFlags);
        return node;
    }

    // ---- ITransformerProvider：NeFobric 没有 LaunchWrapper 式的其他变换器 ----

    @Override
    public Collection<ITransformer> getTransformers() {
        return List.of();
    }

    @Override
    public Collection<ITransformer> getDelegatedTransformers() {
        return List.of();
    }

    @Override
    public void addTransformerExclusion(String name) {
    }

    // ---- IClassTracker ----

    @Override
    public void registerInvalidClass(String className) {
    }

    @Override
    public boolean isClassLoaded(String className) {
        return MixinHost.classLoader().isClassLoaded(className.replace('/', '.'));
    }

    @Override
    public String getClassRestrictions(String className) {
        return "";
    }
}
