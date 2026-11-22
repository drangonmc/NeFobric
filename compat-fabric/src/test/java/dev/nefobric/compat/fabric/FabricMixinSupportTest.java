package dev.nefobric.compat.fabric;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.FabricUtil;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FabricMixinSupportTest {

    private static int compat(String depends) throws Exception {
        String json = "{\"schemaVersion\":1,\"id\":\"mm\",\"version\":\"1\"" + (depends == null ? "" : ",\"depends\":{\"fabricloader\":\"" + depends + "\"}") + "}";
        return FabricMixinSupport.compatibilityFor(FabricMetadataParser.metadata(FabricMetadataParser.parse(json, Paths.get("m.jar"))));
    }

    @Test
    void picksCompatibilityFromLoaderDependency() throws Exception {
        assertEquals(FabricUtil.COMPATIBILITY_0_9_2, compat(null));
        assertEquals(FabricUtil.COMPATIBILITY_0_9_2, compat("*"));
        assertEquals(FabricUtil.COMPATIBILITY_0_9_2, compat(">=0.11.3"));
        assertEquals(FabricUtil.COMPATIBILITY_0_10_0, compat(">=0.14.21"));
        assertEquals(FabricUtil.COMPATIBILITY_0_10_0, compat(">=0.15.0"));
        assertEquals(FabricUtil.COMPATIBILITY_0_14_0, compat(">=0.16.0"));
    }
}
