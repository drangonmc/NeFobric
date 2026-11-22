package dev.nefobric.testmods.forge;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.minecraftforge.accesstransformer.AccessTransformerEngine;
import net.minecraftforge.accesstransformer.parser.AccessTransformerList;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模组在加载早期会碰的 Forge 和 ModLauncher 内部接口，用法照 ModernFix 这类模组：
 * 反射读访问变换引擎的 masterList、运行中追加 AT 文件、往 Launcher 的插件表里加一个启动插件。
 * 在真正的 Forge 1.20.1 里同样能跑。
 */
final class LoaderInternals {
    private LoaderInternals() {
    }

    static void probe() {
        try {
            atEngine();
            lateAccessTransformer();
            launchPlugin();
        } catch (ReflectiveOperationException e) {
            System.out.println("[forge_hello] loader internals failed: " + e);
        }
    }

    private static void atEngine() throws ReflectiveOperationException {
        String game = FMLEnvironment.dist.isClient() ? "net/minecraft/client/FakeMinecraft" : "net/minecraft/server/Main";
        Field master = AccessTransformerEngine.class.getDeclaredField("masterList");
        master.setAccessible(true);
        AccessTransformerList list = (AccessTransformerList) master.get(AccessTransformerEngine.INSTANCE);
        System.out.println("[forge_hello] AT engine: game class targeted=" + list.containsClassTarget(Type.getObjectType(game)));
    }

    /** late_at.cfg 把 LateTarget 的私有方法改成 public；LateTarget 在这之前没有加载过。 */
    private static void lateAccessTransformer() throws ReflectiveOperationException {
        ModFile file = (ModFile) ModList.get().getModFileById("forge_hello").getFile();
        FMLLoader.addAccessTransformer(file.findResource("late_at.cfg"), file);
        Method secret = Class.forName("dev.nefobric.testmods.forge.LateTarget").getDeclaredMethod("secret");
        System.out.println("[forge_hello] late access transformer: secret is "
                + (Modifier.isPublic(secret.getModifiers()) ? "public" : "private"));
    }

    /** 照 ModernFix 的做法：复制一份插件表，加上自己的插件再放回去。PluginTarget 在这之前没有加载过。 */
    @SuppressWarnings("unchecked")
    private static void launchPlugin() throws ReflectiveOperationException {
        Field lp = Launcher.class.getDeclaredField("launchPlugins");
        lp.setAccessible(true);
        Object handler = lp.get(Launcher.INSTANCE);
        Field pf = handler.getClass().getDeclaredField("plugins");
        pf.setAccessible(true);
        Map<String, ILaunchPluginService> plugins = new LinkedHashMap<>((Map<String, ILaunchPluginService>) pf.get(handler));
        HelloLaunchPlugin plugin = new HelloLaunchPlugin();
        plugins.put(plugin.name(), plugin);
        pf.set(handler, plugins);
        Object value = Class.forName("dev.nefobric.testmods.forge.PluginTarget").getMethod("value", boolean.class)
                .invoke(null, true);
        System.out.println("[forge_hello] launch plugin: " + value);
    }
}
