package dev.nefobric.compat.fabric;

import dev.nefobric.loader.metadata.Dependency;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.ModMetadataException;
import dev.nefobric.loader.metadata.SideRestriction;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricMetadataParserTest {

    @Test
    void parsesTypicalModJson() throws Exception {
        ModCandidate m = FabricMetadataParser.parse("""
                {
                  "schemaVersion": 1,
                  "id": "example-mod",
                  "version": "1.2.3+mc1.20.1",
                  "name": "Example",
                  "environment": "client",
                  "entrypoints": {
                    "main": ["com.example.Main", {"value": "com.example.Other::INSTANCE"}],
                    "client": "com.example.Client"
                  },
                  "mixins": ["example.mixins.json", {"config": "example.client.mixins.json", "environment": "client"}],
                  "accessWidener": "example.accesswidener",
                  "jars": [{"file": "META-INF/jars/lib.jar"}],
                  "provides": ["example_alias"],
                  "depends": {"fabricloader": ">=0.14.21", "minecraft": ["1.20", "1.20.1"], "fabric-api": "*"},
                  "recommends": {"modmenu": "*"},
                  "breaks": {"optifabric": "*"}
                }
                """, Paths.get("example.jar"));
        assertEquals("example-mod", m.id());
        assertEquals("1.2.3+mc1.20.1", m.version());
        assertEquals(Ecosystem.FABRIC, m.ecosystem());
        assertEquals(SideRestriction.CLIENT, m.environment());
        assertEquals(List.of("com.example.Main", "com.example.Other::INSTANCE"), m.entrypoints("main"));
        assertEquals(List.of("com.example.Client"), m.entrypoints("client"));
        assertEquals(List.of("example.mixins.json", "example.client.mixins.json"), m.mixinConfigs());
        assertEquals(List.of("example.accesswidener"), m.accessTransformers());
        assertEquals(List.of("META-INF/jars/lib.jar"), m.nestedJars());
        assertEquals(List.of("example_alias"), m.provides());
        assertEquals(5, m.dependencies().size());
        Dependency mc = m.dependencies().stream().filter(d -> d.modId().equals("minecraft")).findFirst().orElseThrow();
        assertTrue(mc.predicate().test("1.20.1"));
        assertTrue(mc.predicate().test("1.20"));
        assertFalse(mc.predicate().test("1.20.2"));
        assertEquals(Dependency.Kind.INCOMPATIBLE,
                m.dependencies().stream().filter(d -> d.modId().equals("optifabric")).findFirst().orElseThrow().kind());
    }

    @Test
    void rejectsBadMetadata() {
        assertThrows(ModMetadataException.class, () -> FabricMetadataParser.parse("{", Paths.get("x.jar")));
        assertThrows(ModMetadataException.class,
                () -> FabricMetadataParser.parse("{\"id\":\"legacy\",\"version\":\"1\"}", Paths.get("x.jar")));
        assertThrows(ModMetadataException.class,
                () -> FabricMetadataParser.parse("{\"schemaVersion\":1,\"id\":\"Bad Id\",\"version\":\"1\"}", Paths.get("x.jar")));
        assertThrows(ModMetadataException.class,
                () -> FabricMetadataParser.parse("{\"schemaVersion\":1,\"id\":\"ok\"}", Paths.get("x.jar")));
        assertThrows(ModMetadataException.class, () -> FabricMetadataParser.parse("""
                {"schemaVersion":1,"id":"ok","version":"1","depends":{"minecraft":">v1"}}
                """, Paths.get("x.jar")));
    }
}
