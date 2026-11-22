package dev.nefobric.forge.fml;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.moddiscovery.ModAnnotation;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModScannerTest {
    @Mod("scanned")
    @Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    static class Sample {
    }

    @Test
    void annotationDataMatchesForgeShape() throws IOException {
        ModFileScanData data = new ModFileScanData();
        try (InputStream in = Sample.class.getResourceAsStream("ModScannerTest$Sample.class")) {
            ModScanner.scanClass(in.readAllBytes(), data);
        }
        Type sample = Type.getType(Sample.class);
        assertTrue(data.getClasses().stream().anyMatch(c -> c.clazz().equals(sample)));
        ModFileScanData.AnnotationData mod = data.getAnnotations().stream()
                .filter(a -> a.annotationType().equals(Type.getType(Mod.class))).findFirst().orElseThrow();
        assertEquals(ElementType.TYPE, mod.targetType());
        assertEquals(sample.getClassName(), mod.memberName());
        assertEquals("scanned", mod.annotationData().get("value"));

        ModFileScanData.AnnotationData ebs = data.getAnnotations().stream()
                .filter(a -> a.annotationType().equals(Type.getType(Mod.EventBusSubscriber.class))).findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<ModAnnotation.EnumHolder> sides = (List<ModAnnotation.EnumHolder>) ebs.annotationData().get("value");
        assertEquals("CLIENT", sides.get(0).getValue());
        assertEquals("MOD", ((ModAnnotation.EnumHolder) ebs.annotationData().get("bus")).getValue());
    }
}
