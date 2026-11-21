package dev.nefobric.compat.forge;

import dev.nefobric.loader.metadata.Dependency;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.ModMetadataException;
import dev.nefobric.loader.metadata.SideRestriction;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModsTomlParserTest {
    private static final String FORGE_TOML = """
            modLoader="javafml" #mandatory
            loaderVersion="[47,)"
            license="All rights reserved"

            [[mods]]
            modId="examplemod"
            version="${file.jarVersion}"
            displayName="Example Mod"
            description='''
            A multi-line
            description.
            '''

            [[mods]]
            modId="example_addon"
            version="2.0"

            [[dependencies.examplemod]]
                modId="forge"
                mandatory=true
                versionRange="[47,)"
                ordering="NONE"
                side="BOTH"
            [[dependencies.examplemod]]
                modId="jei"
                mandatory=false
                versionRange="[15,)"
                ordering="AFTER"
                side="CLIENT"

            [[mixins]]
            config="examplemod.mixins.json"
            """;

    private static ModsTomlParser.JarInfo jar(Map<String, String> classes) {
        return new ModsTomlParser.JarInfo(Paths.get("example.jar"), "1.4.2", classes, List.of(), true, false);
    }

    @Test
    void parsesForgeModsToml() throws Exception {
        List<ModCandidate> mods = ModsTomlParser.parse(FORGE_TOML, jar(Map.of("examplemod", "com.example.ExampleMod")));
        assertEquals(2, mods.size());
        ModCandidate m = mods.get(0);
        assertEquals("examplemod", m.id());
        assertEquals("1.4.2", m.version());
        assertEquals("Example Mod", m.name());
        assertEquals(Ecosystem.FORGE, m.ecosystem());
        assertEquals("com.example.ExampleMod", m.extra(ModsTomlParser.EXTRA_MOD_CLASS, String.class));
        assertEquals(List.of("examplemod.mixins.json"), m.mixinConfigs());
        assertEquals(List.of("META-INF/accesstransformer.cfg"), m.accessTransformers());
        // javafml 加载器版本 + forge + jei
        assertEquals(3, m.dependencies().size());
        Dependency jei = m.dependencies().get(2);
        assertEquals("jei", jei.modId());
        assertEquals(Dependency.Kind.OPTIONAL, jei.kind());
        assertEquals(Dependency.Ordering.AFTER, jei.ordering());
        assertEquals(SideRestriction.CLIENT, jei.side());
        assertTrue(jei.predicate().test("15.2.0"));
        assertFalse(jei.predicate().test("14.0"));
        ModCandidate addon = mods.get(1);
        assertEquals("2.0", addon.version());
        assertNull(addon.extra(ModsTomlParser.EXTRA_MOD_CLASS, String.class));
    }

    @Test
    void neoForgeDependencyMarksNeoForgeMods() throws Exception {
        List<ModCandidate> mods = ModsTomlParser.parse("""
                modLoader="javafml"
                loaderVersion="[47,)"
                [[mods]]
                modId="neo_thing"
                version="1.0"
                [[dependencies.neo_thing]]
                modId="neoforge"
                type="required"
                versionRange="[47.1,)"
                """, jar(Map.of()));
        assertEquals(Ecosystem.NEOFORGE, mods.get(0).ecosystem());
        assertEquals(Dependency.Kind.REQUIRED, mods.get(0).dependencies().get(1).kind());
    }

    @Test
    void missingJarVersionFallsBackLikeForge() throws Exception {
        List<ModCandidate> mods = ModsTomlParser.parse(FORGE_TOML,
                new ModsTomlParser.JarInfo(Paths.get("x.jar"), null, Map.of(), List.of(), false, false));
        assertEquals("0.0NONE", mods.get(0).version());
    }

    @Test
    void rejectsBadToml() {
        assertThrows(ModMetadataException.class, () -> ModsTomlParser.parse("modLoader=", jar(Map.of())));
        assertThrows(ModMetadataException.class, () -> ModsTomlParser.parse("modLoader=\"kotlinforforge\"\n[[mods]]\nmodId=\"a_b\"", jar(Map.of())));
        assertThrows(ModMetadataException.class, () -> ModsTomlParser.parse("modLoader=\"javafml\"", jar(Map.of())));
        assertThrows(ModMetadataException.class, () -> ModsTomlParser.parse("[[mods]]\nmodId=\"Bad-Id\"", jar(Map.of())));
        assertThrows(ModMetadataException.class, () -> ModsTomlParser.parse(
                "[[mods]]\nmodId=\"ok_id\"\n[[dependencies.ok_id]]\nmodId=\"x\"\nversionRange=\"[1.0\"", jar(Map.of())));
    }

    @Test
    void annotationScannerSubstringSearch() {
        assertTrue(ModAnnotationScanner.contains("xxLnet/minecraftforge/fml/common/Mod;yy".getBytes(),
                ModAnnotationScanner.MOD_DESC.getBytes()));
        assertFalse(ModAnnotationScanner.contains("Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;".getBytes(),
                ModAnnotationScanner.MOD_DESC.getBytes()));
    }
}
