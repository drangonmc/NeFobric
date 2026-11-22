package dev.nefobric.loader.discovery;

import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.launch.GameInstall;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.ModMetadataException;
import dev.nefobric.loader.spi.EcosystemAdapter;
import dev.nefobric.loader.spi.LoadStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    // ---- Jar-in-Jar ----

    /** 像 Fabric 一样：jar 里的 test.mod 是 “id 版本 内嵌jar1 内嵌jar2 …”。 */
    private record NestingAdapter() implements EcosystemAdapter {
        @Override
        public String displayName() {
            return "Nesting";
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public boolean hasMetadata(ModJar jar) {
            return jar.has("test.mod");
        }

        @Override
        public List<ModCandidate> readMods(ModJar jar) throws ModMetadataException {
            String[] parts;
            try {
                parts = jar.readString("test.mod").orElseThrow().trim().split(" ");
            } catch (IOException e) {
                throw new ModMetadataException(e.getMessage());
            }
            ModCandidate.Builder b = ModCandidate.builder(parts[0], parts[1], Ecosystem.FABRIC).origin(jar.path());
            for (int i = 2; i < parts.length; i++) {
                b.nestedJar(parts[i]);
            }
            return List.of(b.build());
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
            return true;
        }

        @Override
        public void runStage(LoadStage stage, NeFobricLoader loader, List<ModCandidate> mods) {
        }
    }

    /** 条目名 → 内容（String 或 byte[]）。 */
    private static byte[] zip(Map<String, Object> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, Object> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue() instanceof byte[] b ? b : ((String) e.getValue()).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] modJar(String meta, Object... nested) throws IOException {
        Map<String, Object> entries = new LinkedHashMap<>();
        entries.put("test.mod", meta);
        for (int i = 0; i < nested.length; i += 2) {
            entries.put((String) nested[i], nested[i + 1]);
        }
        return zip(entries);
    }

    private static ModCandidate byId(ModDiscoverer.Result r, String id) {
        return r.candidates().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void nestedJarsAreExtractedAndReadRecursively(@TempDir Path dir) throws IOException {
        byte[] deep = modJar("deep 1.0.0");
        byte[] lib = zip(Map.of("lib/Lib.class", "not really a class"));
        byte[] middle = modJar("middle 2.0.0 META-INF/jars/deep.jar", "META-INF/jars/deep.jar", deep);
        Path top = dir.resolve("top.jar");
        Files.write(top, modJar("top 3.0.0 META-INF/jars/middle.jar META-INF/jars/lib.jar",
                "META-INF/jars/middle.jar", middle, "META-INF/jars/lib.jar", lib));
        Path cache = dir.resolve("cache");

        ModDiscoverer.Result r = ModDiscoverer.read(List.of(top), List.of(new NestingAdapter()), cache);
        assertTrue(r.errors().isEmpty(), r.errors().toString());
        assertEquals(List.of("top", "middle", "deep"), r.candidates().stream().map(ModCandidate::id).toList());

        ModCandidate t = byId(r, "top");
        ModCandidate m = byId(r, "middle");
        ModCandidate d = byId(r, "deep");
        assertNull(t.parent());
        assertFalse(t.isNested());
        assertSame(t, m.parent());
        assertSame(m, d.parent());
        assertEquals("META-INF/jars/middle.jar", m.nestedPath());
        assertEquals(List.of(m), t.children());
        assertEquals("top.jar!/middle.jar!/deep.jar", d.fileName());
        assertTrue(m.origin().startsWith(cache), m.origin().toString());
        assertTrue(Files.isRegularFile(d.origin()));

        // 没有模组元数据的内嵌 jar 是普通库
        assertEquals(1, r.libraries().size());
        NestedLibrary l = r.libraries().get(0);
        assertSame(t, l.parent());
        assertEquals("META-INF/jars/lib.jar", l.pathInParent());
        assertTrue(l.path().startsWith(cache));
    }

    @Test
    void sameNestedJarInTwoModsIsExtractedOnceButReadForEach(@TempDir Path dir) throws IOException {
        byte[] shared = modJar("shared_lib 1.0.0");
        Path a = dir.resolve("a.jar");
        Path b = dir.resolve("b.jar");
        Files.write(a, modJar("a 1.0.0 META-INF/jars/shared.jar", "META-INF/jars/shared.jar", shared));
        Files.write(b, modJar("b 1.0.0 META-INF/jars/shared.jar", "META-INF/jars/shared.jar", shared));
        Path cache = dir.resolve("cache");

        ModDiscoverer.Result r = ModDiscoverer.read(List.of(a, b), List.of(new NestingAdapter()), cache);
        assertTrue(r.errors().isEmpty(), r.errors().toString());
        List<ModCandidate> copies = r.candidates().stream().filter(c -> c.id().equals("shared_lib")).toList();
        assertEquals(2, copies.size());
        assertEquals(List.of("a", "b"), copies.stream().map(c -> c.parent().id()).toList());
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.walk(cache)) {
            s.filter(Files::isRegularFile).forEach(files::add);
        }
        assertEquals(1, files.size(), files.toString());
    }

    @Test
    void missingNestedJarIsAWarningAndOtherNestedJarsStillLoad(@TempDir Path dir) throws IOException {
        Path top = dir.resolve("top.jar");
        Files.write(top, modJar("top 1.0.0 META-INF/jars/gone.jar META-INF/jars/here.jar",
                "META-INF/jars/here.jar", modJar("here 1.0.0")));
        ModDiscoverer.Result r = ModDiscoverer.read(List.of(top), List.of(new NestingAdapter()), dir.resolve("cache"));
        assertTrue(r.errors().isEmpty(), r.errors().toString());
        assertEquals(1, r.warnings().size(), r.warnings().toString());
        assertTrue(r.warnings().get(0).contains("META-INF/jars/gone.jar"), r.warnings().get(0));
        assertEquals(List.of("top", "here"), r.candidates().stream().map(ModCandidate::id).toList());
    }

    @Test
    void withoutCacheDirNestedJarsAreNotRead(@TempDir Path dir) throws IOException {
        Path top = dir.resolve("top.jar");
        Files.write(top, modJar("top 1.0.0 META-INF/jars/inner.jar", "META-INF/jars/inner.jar", modJar("inner 1.0.0")));
        ModDiscoverer.Result r = ModDiscoverer.read(List.of(top), List.of(new NestingAdapter()));
        assertEquals(List.of("top"), r.candidates().stream().map(ModCandidate::id).toList());
        assertTrue(r.libraries().isEmpty());
    }

    @Test
    void nestingDeeperThanTheLimitStops(@TempDir Path dir) throws IOException {
        byte[] jar = modJar("level" + (ModDiscoverer.MAX_DEPTH + 1) + " 1.0.0");
        for (int i = ModDiscoverer.MAX_DEPTH; i >= 0; i--) {
            jar = modJar("level" + i + " 1.0.0 META-INF/jars/next.jar", "META-INF/jars/next.jar", jar);
        }
        Path top = dir.resolve("top.jar");
        Files.write(top, jar);
        ModDiscoverer.Result r = ModDiscoverer.read(List.of(top), List.of(new NestingAdapter()), dir.resolve("cache"));
        assertEquals(ModDiscoverer.MAX_DEPTH + 1, r.candidates().size());
        assertEquals(1, r.warnings().size(), r.warnings().toString());
    }

    @Test
    void extractReusesAnExistingFile(@TempDir Path dir) throws IOException {
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);
        Path first = ModDiscoverer.extract(dir, "a9993e364706816aba3e25717850c26c9cd0d89d", "META-INF/jars/x.jar", data);
        assertEquals(dir.resolve("a9").resolve("a9993e364706816aba3e25717850c26c9cd0d89d").resolve("x.jar"), first);
        Files.setLastModifiedTime(first, java.nio.file.attribute.FileTime.fromMillis(0));
        Path second = ModDiscoverer.extract(dir, "a9993e364706816aba3e25717850c26c9cd0d89d", "META-INF/jars/x.jar", data);
        assertEquals(first, second);
        assertEquals(0, Files.getLastModifiedTime(second).toMillis());
        try (Stream<Path> s = Files.list(first.getParent())) {
            assertEquals(1, s.count(), "no leftover temp files");
        }
    }
}
