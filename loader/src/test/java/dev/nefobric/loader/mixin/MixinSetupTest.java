package dev.nefobric.loader.mixin;

import dev.nefobric.loader.Env;
import dev.nefobric.loader.classloading.NeFobricClassLoader;
import dev.nefobric.loader.discovery.ModJar;
import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.launch.GameInstall;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.spi.EcosystemAdapter;
import dev.nefobric.loader.spi.LoadStage;
import dev.nefobric.loader.transform.TransformerPhase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用一个真正的模组 jar（目标类 + Mixin + MixinExtras + 插件 + 配置）把 Mixin 整条链路跑一遍。
 * Mixin 是进程级的全局状态，所以整个测试类只启动一次。
 */
class MixinSetupTest {
    private static final String GREETER = "fixture.target.Greeter";
    private static NeFobricClassLoader loader;
    private static final List<String> decorated = new ArrayList<>();
    private static IMixinConfig decoratedConfig;
    private static String pluginLoadedAtStart;

    @BeforeAll
    static void startMixin() {
        Path fixture = Path.of(System.getProperty("nefobric.test.mixinFixture"));
        assertTrue(Files.isRegularFile(fixture), fixture.toString());
        loader = new NeFobricClassLoader(List.of(fixture), MixinSetupTest.class.getClassLoader(), List.of());
        // 模拟 AW/AT：ACCESS 阶段把私有字段 name 改成 public
        loader.transformers().add(TransformerPhase.ACCESS,
                (name, bytes) -> name.equals(GREETER) ? makeFieldPublic(bytes, "name") : bytes);
        ModCandidate mod = ModCandidate.builder("fixture", "1.0.0", Ecosystem.FABRIC).build();
        List<String> problems = MixinSetup.start(loader, Env.CLIENT,
                List.of(new MixinSetup.ConfigRequest("fixture.mixins.json", mod, new RecordingAdapter())));
        assertEquals(List.of(), problems);
        pluginLoadedAtStart = System.getProperty("fixture.plugin.loaded");
    }

    @Test
    void pluginOnLoadRunsBeforeAnyGameClassIsLoaded() {
        assertEquals("fixture.mixin", pluginLoadedAtStart);
        assertEquals(List.of("fixture.mixins.json"), decorated);
    }

    @Test
    void adapterDecorationsReachTheConfig() {
        // 选择过的配置不再出现在 Mixins.getConfigs() 里，用装饰时拿到的对象检查
        assertEquals("fixture.mixins.json", decoratedConfig.getName());
        assertEquals("fixture", decoratedConfig.getDecoration(FabricUtil.KEY_MOD_ID));
        assertEquals("fixture.mixin.", decoratedConfig.getMixinPackage());
    }

    @Test
    void mixinsApply() throws Exception {
        Class<?> greeter = Class.forName(GREETER, true, loader);
        assertEquals(loader, greeter.getClassLoader());
        Object g = greeter.getConstructor().newInstance();
        assertEquals("hello world!", greeter.getMethod("greet").invoke(g), "@Inject");
        assertEquals(2, greeter.getMethod("count").invoke(g), "MixinExtras @ModifyReturnValue");
        // @ModifyArgs 依赖 Mixin 生成的合成类，类加载器要能定义它
        assertEquals("y+x", greeter.getMethod("join", String.class, String.class).invoke(g, "x", "y"), "@ModifyArgs");
    }

    @Test
    void accessorInterfaceWorks() throws Exception {
        Class<?> greeter = Class.forName(GREETER, true, loader);
        Object g = greeter.getConstructor().newInstance();
        Class<?> accessor = Class.forName("fixture.mixin.GreeterAccessor", true, loader);
        assertTrue(accessor.isInstance(g));
        Method set = accessor.getMethod("fixture$setName", String.class);
        set.invoke(g, "NeFobric");
        assertEquals("NeFobric", accessor.getMethod("fixture$getName").invoke(g));
        assertEquals("hello NeFobric!", greeter.getMethod("greet").invoke(g));
    }

    @Test
    void accessPhaseRunsBeforeMixinAndInTheFinalClass() throws Exception {
        Class<?> greeter = Class.forName(GREETER, true, loader);
        Field name = greeter.getDeclaredField("name");
        assertTrue(Modifier.isPublic(name.getModifiers()));

        ClassNode pre = new NeFobricMixinService().getClassNode(GREETER, true);
        FieldNode field = pre.fields.stream().filter(f -> f.name.equals("name")).findFirst().orElseThrow();
        assertTrue((field.access & Opcodes.ACC_PUBLIC) != 0, "Mixin sees the ACCESS phase result");
        assertFalse(pre.interfaces.contains("fixture/mixin/GreeterAccessor"), "but not the Mixin result");
        ClassNode raw = new NeFobricMixinService().getClassNode(GREETER, false);
        assertTrue((raw.fields.stream().filter(f -> f.name.equals("name")).findFirst().orElseThrow().access
                & Opcodes.ACC_PRIVATE) != 0);
    }

    @Test
    void bytecodeProviderReadsJdkClassesFromTheParent() throws Exception {
        ClassNode object = new NeFobricMixinService().getClassNode("java/lang/Object");
        assertEquals("java/lang/Object", object.name);
        assertTrue(new NeFobricMixinService().isClassLoaded(GREETER) || !loader.isClassLoaded(GREETER));
    }

    private static byte[] makeFieldPublic(byte[] bytes, String field) {
        ClassReader r = new ClassReader(bytes);
        ClassWriter w = new ClassWriter(0);
        r.accept(new ClassVisitor(Opcodes.ASM9, w) {
            @Override
            public FieldVisitor visitField(int access, String name, String desc, String sig, Object value) {
                if (name.equals(field)) {
                    access = (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
                }
                return super.visitField(access, name, desc, sig, value);
            }
        }, 0);
        return w.toByteArray();
    }

    /** 只用到装饰回调的兼容层。 */
    private static final class RecordingAdapter implements EcosystemAdapter {
        @Override
        public void decorateMixinConfig(ModCandidate mod, String resource, IMixinConfig config) {
            decorated.add(resource);
            decoratedConfig = config;
            config.decorate(FabricUtil.KEY_MOD_ID, mod.id());
        }

        @Override
        public String displayName() {
            return "Test";
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public boolean hasMetadata(ModJar jar) {
            return false;
        }

        @Override
        public List<ModCandidate> readMods(ModJar jar) {
            return List.of();
        }

        @Override
        public List<ModCandidate> builtinMods(GameInstall game) {
            return List.of();
        }

        @Override
        public Collection<String> loaderOwnedClasses() {
            return List.of();
        }

        @Override
        public boolean owns(ModCandidate mod) {
            return true;
        }

        @Override
        public void runStage(LoadStage stage, NeFobricLoader loader, List<ModCandidate> mods) {
        }
    }
}
