package dev.nefobric.compat.forge;

import dev.nefobric.loader.discovery.ModJar;
import dev.nefobric.loader.metadata.ModCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForgeJarJarTest {
    private static final String TOML = """
            modLoader="javafml"
            loaderVersion="[47,)"
            license="MIT"
            [[mods]]
            modId="jarjar_host"
            version="1.0.0"
            """;

    private static Path jar(Path dir, boolean withMetadata) throws Exception {
        Path jar = dir.resolve(withMetadata ? "host.jar" : "host-plain.jar");
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String entry : List.of("META-INF/mods.toml", "META-INF/jarjar/b-lib-2.0.jar",
                    "META-INF/jarjar/a-lib-1.0.jar", "META-INF/jarjar/notes/c.jar", "META-INF/jarjar/readme.txt",
                    "META-INF/jarjar/metadata.json")) {
                if (entry.endsWith("metadata.json") && !withMetadata) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(entry));
                zip.write((entry.endsWith("mods.toml") ? TOML : "x").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return jar;
    }

    @Test
    void jarJarListsTheJarsDirectlyInTheJarJarFolder(@TempDir Path dir) throws Exception {
        ForgeAdapter adapter = new ForgeAdapter();
        try (ModJar mj = ModJar.open(jar(dir, true))) {
            ModCandidate mod = adapter.readMods(mj).get(0);
            assertEquals(List.of("META-INF/jarjar/a-lib-1.0.jar", "META-INF/jarjar/b-lib-2.0.jar"),
                    adapter.nestedJars(mod, mj));
        }
    }

    @Test
    void withoutJarJarMetadataNothingIsNested(@TempDir Path dir) throws Exception {
        ForgeAdapter adapter = new ForgeAdapter();
        try (ModJar mj = ModJar.open(jar(dir, false))) {
            ModCandidate mod = adapter.readMods(mj).get(0);
            assertEquals(List.of(), adapter.nestedJars(mod, mj));
        }
    }
}
