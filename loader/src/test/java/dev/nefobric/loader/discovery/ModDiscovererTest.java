package dev.nefobric.loader.discovery;

import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.launch.GameInstall;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.ModMetadataException;
import dev.nefobric.loader.spi.EcosystemAdapter;
import dev.nefobric.loader.spi.LoadStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModDiscovererTest {

    /** 认得 test.meta，读的时候按构造参数抛出跳过或普通的元数据异常。 */
    private record ThrowingAdapter(ModMetadataException toThrow) implements EcosystemAdapter {
        @Override
        public String displayName() {
            return "Test";
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public boolean hasMetadata(ModJar jar) {
            return jar.has("test.meta");
        }

        @Override
        public List<ModCandidate> readMods(ModJar jar) throws ModMetadataException {
            throw toThrow;
        }

        @Override
        public List<ModCandidate> builtinMods(GameInstall game) {
            return List.of();
        }

        @Override
        public Collection<String> loaderOwnedClasses() {
            return List.of();
        }

        @Override
        public boolean owns(ModCandidate mod) {
            return false;
        }

        @Override
        public void runStage(LoadStage stage, NeFobricLoader loader, List<ModCandidate> mods) {
        }
    }

    private static Path jarWithMeta(Path dir) throws IOException {
        Path jar = dir.resolve("some-mod.jar");
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("test.meta"));
            zip.write("x".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return jar;
    }

    @Test
    void skipExceptionBecomesWarningNotError(@TempDir Path dir) throws IOException {
        Path jar = jarWithMeta(dir);
        ModDiscoverer.Result r = ModDiscoverer.read(List.of(jar),
                List.of(new ThrowingAdapter(ModMetadataException.skip("not for this game version"))));
        assertTrue(r.errors().isEmpty(), r.errors().toString());
        assertEquals(1, r.warnings().size());
        assertTrue(r.warnings().get(0).contains("some-mod.jar"), r.warnings().get(0));
        assertTrue(r.warnings().get(0).contains("not for this game version"), r.warnings().get(0));
        assertTrue(r.candidates().isEmpty());
    }

    @Test
    void ordinaryMetadataExceptionIsStillAnError(@TempDir Path dir) throws IOException {
        Path jar = jarWithMeta(dir);
        ModDiscoverer.Result r = ModDiscoverer.read(List.of(jar),
                List.of(new ThrowingAdapter(new ModMetadataException("broken"))));
        assertEquals(1, r.errors().size());
        assertTrue(r.errors().get(0).contains("broken"), r.errors().get(0));
        assertTrue(r.warnings().isEmpty());
    }
}
