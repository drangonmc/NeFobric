package dev.nefobric.testmods.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * 只用 Forge 47 公开 API 的测试模组：构造函数里拿模组总线、注册配置；监听 setup 和加载完成事件。
 * 同一个 jar 也能直接放进真正的 Forge 1.20.1 里运行。
 */
@Mod("forge_hello")
public class ForgeHello {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.ConfigValue<String> GREETING = BUILDER
            .comment("What forge_hello says during common setup")
            .define("greeting", "Hello from the config");
    private static final ForgeConfigSpec.IntValue COUNT = BUILDER.defineInRange("count", 3, 1, 10);
    private static final ForgeConfigSpec SPEC = BUILDER.build();
    private static final Capability<HelloCapability> HELLO = CapabilityManager.get(new CapabilityToken<>() {
    });

    @SuppressWarnings("removal") // 1.20.1 的模组都这样拿上下文；Forge 只是预告了 1.21.1 会移除
    public ForgeHello() {
        ClassLoader cl = ForgeHello.class.getClassLoader();
        System.out.println("[forge_hello] constructed: Hello from Forge! / 你好，Forge！ thread="
                + Thread.currentThread().getName() + ", loader=" + (cl == null ? "bootstrap" : cl.getName()));
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
        probeTransformers();
        LoaderInternals.probe();
        HELLO.addListener(cap -> {
            LazyOptional<HelloCapability> impl = LazyOptional.of(() -> () -> "hi");
            System.out.println("[forge_hello] capability registered: " + cap.getName() + ", greet="
                    + cap.orEmpty(HELLO, impl).map(HelloCapability::greet).orElse("none"));
        });
    }

    /** 检查 access transformer 和 @OnlyIn 剥离：在真正的 Forge 里没有模拟游戏的类，只检查 @OnlyIn。 */
    private static void probeTransformers() {
        String game = FMLEnvironment.dist.isClient() ? "net.minecraft.client.FakeMinecraft" : "net.minecraft.server.Main";
        try {
            Method probe = Class.forName(game, false, ForgeHello.class.getClassLoader()).getDeclaredMethod("atProbe");
            System.out.println("[forge_hello] access transformer: atProbe is "
                    + (Modifier.isPublic(probe.getModifiers()) ? "public" : "private"));
        } catch (ReflectiveOperationException e) {
            // 不是模拟游戏
        }
        boolean present = Arrays.stream(ForgeHello.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals("clientOnly"));
        System.out.println("[forge_hello] @OnlyIn: clientOnly " + (present ? "present" : "stripped"));
    }

    @OnlyIn(Dist.CLIENT)
    private static String clientOnly() {
        return "client";
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        System.out.println("[forge_hello] common setup: " + GREETING.get() + " x" + COUNT.get()
                + ", mods=" + ModList.get().size() + ", forge=" + ModList.get().isLoaded("forge"));
        event.enqueueWork(() -> System.out.println("[forge_hello] enqueued work ran"));
    }

    @Mod.EventBusSubscriber(modid = "forge_hello", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onLoadComplete(FMLLoadCompleteEvent event) {
            System.out.println("[forge_hello] load complete");
        }
    }
}
