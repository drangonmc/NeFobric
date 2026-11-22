package net.minecraftforge.fml.javafmlmod;

import net.minecraftforge.fml.Logging;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.forgespi.language.ILifecycleEvent;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.IModLanguageProvider;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;

import java.lang.annotation.ElementType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** {@code javafml}：在扫描结果里找 {@code @Mod} 注解，为每个模组建一个 {@link FMLModContainer}。 */
public class FMLJavaModLanguageProvider implements IModLanguageProvider {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final Type MODANNOTATION = Type.getType("Lnet/minecraftforge/fml/common/Mod;");

    private static final class FMLModTarget implements IModLanguageLoader {
        private final String className;
        private final String modId;

        private FMLModTarget(String className, String modId) {
            this.className = className;
            this.modId = modId;
        }

        public String getModId() {
            return modId;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T loadMod(final IModInfo info, final ModFileScanData modFileScanResults, ModuleLayer gameLayer) {
            try {
                return (T) new FMLModContainer(info, className, modFileScanResults, gameLayer);
            } catch (ModLoadingException e) {
                throw e;
            } catch (Throwable t) {
                throw new ModLoadingException(info, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmodclass", t);
            }
        }
    }

    @Override
    public String name() {
        return "javafml";
    }

    @Override
    public Consumer<ModFileScanData> getFileVisitor() {
        return scanResult -> {
            Map<String, FMLModTarget> modTargetMap = new LinkedHashMap<>();
            for (ModFileScanData.AnnotationData ad : scanResult.getAnnotations()) {
                if (ad.targetType() == ElementType.TYPE && MODANNOTATION.equals(ad.annotationType())) {
                    String modid = (String) ad.annotationData().get("value");
                    String modClass = ad.clazz().getClassName();
                    LOGGER.debug(Logging.SCAN, "Found @Mod class {} with id {}", modClass, modid);
                    modTargetMap.putIfAbsent(modid, new FMLModTarget(modClass, modid));
                }
            }
            scanResult.addLanguageLoader(modTargetMap);
        };
    }

    @Override
    public <R extends ILifecycleEvent<R>> void consumeLifecycleEvent(final Supplier<R> consumeEvent) {
    }
}
