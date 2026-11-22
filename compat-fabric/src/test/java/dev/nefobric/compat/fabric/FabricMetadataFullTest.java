package dev.nefobric.compat.fabric;

import dev.nefobric.compat.fabric.metadata.FabricModMetadata;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.SideRestriction;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.Person;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** fabric.mod.json 的全部字段，样例仿照 Fabric API 模块和常见模组的真实写法。 */
class FabricMetadataFullTest {

    private static final String FABRIC_API_MODULE = """
            {
              "schemaVersion": 1,
              "id": "fabric-resource-loader-v0",
              "name": "Fabric Resource Loader (v0)",
              "version": "0.11.10+bcd08ed377",
              "environment": "*",
              "license": "Apache-2.0",
              "icon": "assets/fabric-resource-loader-v0/icon.png",
              "contact": {
                "homepage": "https://fabricmc.net",
                "irc": "irc://irc.esper.net:6667/fabric",
                "issues": "https://github.com/FabricMC/fabric/issues",
                "sources": "https://github.com/FabricMC/fabric"
              },
              "authors": ["FabricMC"],
              "depends": {"fabricloader": ">=0.14.22"},
              "description": "Asset and data resource loading.",
              "mixins": [
                "fabric-resource-loader-v0.mixins.json",
                {"config": "fabric-resource-loader-v0.client.mixins.json", "environment": "client"}
              ],
              "accessWidener": "fabric-resource-loader-v0.accesswidener",
              "entrypoints": {
                "client": ["net.fabricmc.fabric.impl.resource.loader.client.ResourceLoaderClient"]
              },
              "custom": {
                "fabric-api:module-lifecycle": "stable",
                "modmenu": {"badges": ["library"], "parent": "fabric-api", "update_checker": true},
                "loom:injected_interfaces": {"net/minecraft/class_3264": ["net/fabricmc/fabric/api/X"]},
                "weight": 1.5,
                "nothing": null
              }
            }
            """;

    @Test
    void readsEveryField() throws Exception {
        ModCandidate c = FabricMetadataParser.parse(FABRIC_API_MODULE, Paths.get("fabric-resource-loader-v0.jar"));
        FabricModMetadata m = FabricMetadataParser.metadata(c);
        assertEquals("fabric", m.getType());
        assertEquals("fabric-resource-loader-v0", m.getId());
        assertEquals("0.11.10+bcd08ed377", m.getVersion().getFriendlyString());
        assertEquals("Fabric Resource Loader (v0)", m.getName());
        assertEquals("Asset and data resource loading.", m.getDescription());
        assertEquals(ModEnvironment.UNIVERSAL, m.getEnvironment());
        assertEquals(List.of("Apache-2.0"), List.copyOf(m.getLicense()));
        assertEquals("assets/fabric-resource-loader-v0/icon.png", m.getIconPath(64).orElseThrow());
        assertEquals("https://fabricmc.net", m.getContact().get("homepage").orElseThrow());
        assertEquals(4, m.getContact().asMap().size());
        assertEquals("FabricMC", m.getAuthors().iterator().next().getName());
        assertEquals("fabric-resource-loader-v0.accesswidener", m.accessWidener());
        assertEquals(List.of("fabric-resource-loader-v0.mixins.json", "fabric-resource-loader-v0.client.mixins.json"),
                m.mixinConfigs(EnvType.CLIENT));
        assertEquals(List.of("fabric-resource-loader-v0.mixins.json"), m.mixinConfigs(EnvType.SERVER));
        assertEquals("net.fabricmc.fabric.impl.resource.loader.client.ResourceLoaderClient",
                m.entrypoints("client").get(0).value());
        assertEquals("default", m.entrypoints("client").get(0).adapter());

        ModDependency loader = m.getDependencies().iterator().next();
        assertEquals(ModDependency.Kind.DEPENDS, loader.getKind());
        assertEquals("fabricloader", loader.getModId());
        assertTrue(loader.matches(net.fabricmc.loader.api.Version.parse("0.16.10")));
        assertEquals(1, m.getDepends().size());
        assertTrue(m.getBreaks().isEmpty());

        assertEquals("stable", m.getCustomValue("fabric-api:module-lifecycle").getAsString());
        CustomValue.CvObject modmenu = m.getCustomValue("modmenu").getAsObject();
        assertEquals("library", modmenu.get("badges").getAsArray().get(0).getAsString());
        assertEquals("fabric-api", modmenu.get("parent").getAsString());
        assertTrue(modmenu.get("update_checker").getAsBoolean());
        assertEquals(1.5, m.getCustomValue("weight").getAsNumber().doubleValue());
        assertEquals(CustomValue.CvType.NULL, m.getCustomValue("nothing").getType());
        assertThrows(ClassCastException.class, () -> m.getCustomValue("weight").getAsString());
        assertTrue(m.containsCustomValue("loom:injected_interfaces"));
        assertFalse(m.containsCustomValue("missing"));

        // 核心看到的部分
        assertEquals(SideRestriction.ANY, c.environment());
        assertTrue(c.dependencies().get(0).predicate().test("0.16.10"));
        assertFalse(c.dependencies().get(0).predicate().test("0.14.21"));
    }

    @Test
    void readsPeopleIconsAdaptersAndAllDependencyKinds() throws Exception {
        ModCandidate c = FabricMetadataParser.parse("""
                {
                  "schemaVersion": 1,
                  "id": "kotlin-mod",
                  "version": "2.0.0-beta.1",
                  "environment": "CLIENT",
                  "authors": [{"name": "Alice", "contact": {"email": "a@example.com"}}, "Bob"],
                  "contributors": ["Carol"],
                  "license": ["MIT", "CC0-1.0"],
                  "icon": {"16": "icon16.png", "32": "icon32.png", "128": "icon128.png"},
                  "languageAdapters": {"kotlin": "net.fabricmc.language.kotlin.KotlinAdapter"},
                  "entrypoints": {
                    "main": [{"adapter": "kotlin", "value": "com.example.Mod"}],
                    "modmenu": ["com.example.ModMenuApi"]
                  },
                  "depends": {"fabric-language-kotlin": ">=1.10.0+kotlin.1.9.0", "minecraft": ["1.20", "1.20.1"]},
                  "recommends": {"modmenu": "*"},
                  "suggests": {"flamingo": "*"},
                  "conflicts": {"optifabric": "*"},
                  "breaks": {"sodium": "<0.5.0"},
                  "jars": [{"file": "META-INF/jars/lib.jar"}],
                  "provides": ["kotlinmod"]
                }
                """, Paths.get("k.jar"));
        FabricModMetadata m = FabricMetadataParser.metadata(c);
        assertEquals(ModEnvironment.CLIENT, m.getEnvironment());
        assertEquals(SideRestriction.CLIENT, c.environment());
        Person alice = m.getAuthors().iterator().next();
        assertEquals("Alice", alice.getName());
        assertEquals("a@example.com", alice.getContact().get("email").orElseThrow());
        assertEquals(2, m.getAuthors().size());
        assertEquals("Carol", m.getContributors().iterator().next().getName());
        assertEquals(List.of("MIT", "CC0-1.0"), List.copyOf(m.getLicense()));
        assertEquals("icon16.png", m.getIconPath(1).orElseThrow());
        assertEquals("icon32.png", m.getIconPath(20).orElseThrow());
        assertEquals("icon128.png", m.getIconPath(64).orElseThrow());
        assertEquals("icon128.png", m.getIconPath(512).orElseThrow());
        assertEquals("kotlin", m.entrypoints("main").get(0).adapter());
        assertEquals("net.fabricmc.language.kotlin.KotlinAdapter", m.languageAdapters().get("kotlin"));
        assertEquals(6, m.getDependencies().size());
        assertEquals(1, m.getRecommends().size());
        assertEquals(1, m.getSuggests().size());
        assertEquals(1, m.getConflicts().size());
        assertEquals(1, m.getBreaks().size());
        assertEquals(List.of("META-INF/jars/lib.jar"), m.jars());
        assertEquals(List.of("kotlinmod"), List.copyOf(m.getProvides()));
        assertTrue(m.getIconPath(16).isPresent());
        assertEquals("kotlin-mod", m.getName());
    }
}
