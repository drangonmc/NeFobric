package dev.nefobric.forge.fml;

import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLanguageProvider;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.lowcodemod.LowCodeModContainer;
import net.minecraftforge.fml.mclanguageprovider.MinecraftModContainer;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.IModLanguageProvider;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 为一个模组文件建容器：javafml 按 {@code @Mod} 类，lowcodefml 没有主类，内置文件给 minecraft/forge。 */
public final class ModContainerFactory {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final FMLJavaModLanguageProvider JAVAFML = new FMLJavaModLanguageProvider();

    private ModContainerFactory() {
    }

    public static List<ModContainer> build(ModFileInfo file, List<ModLoadingException> errors) {
        List<ModContainer> out = new ArrayList<>();
        if (ForgeEnvironment.isBuiltinFile(file)) {
            file.getMods().forEach(m -> out.add("minecraft".equals(m.getModId()) ? new MinecraftModContainer(m)
                    : new BuiltinModContainer(m)));
            return out;
        }
        ModFileScanData scan = file.getFile().getScanResult();
        String language = file.requiredLanguageLoaders().isEmpty() ? "javafml"
                : file.requiredLanguageLoaders().get(0).languageName();
        if ("lowcodefml".equals(language)) {
            file.getMods().forEach(m -> out.add(new LowCodeModContainer(m, scan, FMLLoader.getGameLayer())));
            return out;
        }
        JAVAFML.getFileVisitor().accept(scan);
        Map<String, ? extends IModLanguageProvider.IModLanguageLoader> targets = scan.getTargets();
        for (IModInfo info : file.getMods()) {
            IModLanguageProvider.IModLanguageLoader loader = targets.get(info.getModId());
            if (loader == null) {
                // Forge 会因此拒绝整个文件；NeFobric 给一个没有主类的容器，让同一 jar 里的其他模组照常加载
                LOGGER.warn("Mod {} in {} has no @Mod class; loading it without one", info.getModId(),
                        file.getFile().getFileName());
                out.add(new LowCodeModContainer(info, scan, FMLLoader.getGameLayer()));
                continue;
            }
            try {
                out.add(loader.loadMod(info, scan, FMLLoader.getGameLayer()));
            } catch (ModLoadingException e) {
                errors.add(e);
            } catch (Throwable t) {
                errors.add(new ModLoadingException(info, ModLoadingStage.CONSTRUCT,
                        "fml.modloading.failedtoloadmodclass", t));
            }
        }
        return out;
    }
}
