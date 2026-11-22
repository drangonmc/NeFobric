package net.minecraftforge.fml.javafmlmod;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.Logging;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.moddiscovery.ModAnnotation;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 把带 {@code @Mod.EventBusSubscriber} 的类注册到对应总线（只注册属于当前模组、当前端的类）。 */
public class AutomaticEventSubscriber {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Type AUTO_SUBSCRIBER = Type.getType(Mod.EventBusSubscriber.class);
    private static final Type MOD_TYPE = Type.getType(Mod.class);

    public static void inject(final ModContainer mod, final ModFileScanData scanData, final ClassLoader loader) {
        if (scanData == null) {
            return;
        }
        LOGGER.debug(Logging.LOADING, "Attempting to inject @EventBusSubscriber classes into the eventbus for {}",
                mod.getModId());
        Map<String, String> modids = new HashMap<>();
        for (ModFileScanData.AnnotationData ad : scanData.getAnnotations()) {
            if (MOD_TYPE.equals(ad.annotationType())) {
                modids.put(ad.clazz().getClassName(), (String) ad.annotationData().get("value"));
            }
        }
        for (ModFileScanData.AnnotationData ad : scanData.getAnnotations()) {
            if (!AUTO_SUBSCRIBER.equals(ad.annotationType())) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<ModAnnotation.EnumHolder> sidesValue = (List<ModAnnotation.EnumHolder>) ad.annotationData()
                    .getOrDefault("value", List.of(new ModAnnotation.EnumHolder(null, "CLIENT"),
                            new ModAnnotation.EnumHolder(null, "DEDICATED_SERVER")));
            EnumSet<Dist> sides = EnumSet.noneOf(Dist.class);
            sidesValue.forEach(eh -> sides.add(Dist.valueOf(eh.getValue())));
            String modId = (String) ad.annotationData().getOrDefault("modid",
                    modids.getOrDefault(ad.clazz().getClassName(), mod.getModId()));
            ModAnnotation.EnumHolder busHolder = (ModAnnotation.EnumHolder) ad.annotationData()
                    .getOrDefault("bus", new ModAnnotation.EnumHolder(null, "FORGE"));
            Mod.EventBusSubscriber.Bus bus = Mod.EventBusSubscriber.Bus.valueOf(busHolder.getValue());
            if (Objects.equals(mod.getModId(), modId) && sides.contains(FMLEnvironment.dist)) {
                try {
                    LOGGER.debug(Logging.LOADING, "Auto-subscribing {} to {}", ad.clazz().getClassName(), bus);
                    bus.bus().get().register(Class.forName(ad.clazz().getClassName(), true, loader));
                } catch (ClassNotFoundException e) {
                    LOGGER.fatal(Logging.LOADING, "Failed to load mod class {} for @EventBusSubscriber annotation",
                            ad.clazz(), e);
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
