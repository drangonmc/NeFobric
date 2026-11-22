package dev.nefobric.compat.forge.neoforge;

import dev.nefobric.compat.forge.ForgeAdapter;
import dev.nefobric.loader.discovery.ModJar;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.ModMetadataException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 用真实的 jar 文件走一遍 ForgeAdapter.readMods，看 net.neoforged 的 @Mod 会不会被认出来。 */
class ModernNeoForgeJarTest {
    private static final String TOML = """
            modLoader="javafml"
            loaderVersion="[1,)"
            [[mods]]
            modId="newmod"
            version="1.0"
            """;

    private static byte[] modClass(String internalName, String annotationDesc, String modId) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        AnnotationVisitor av = cw.visitAnnotation(annotationDesc, true);
        av.visit("value", modId);
        av.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static Path jar(Path dir, String name, byte[]... classes) throws IOException {
        Path jar = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("META-INF/mods.toml"));
            zip.write(TOML.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (int i = 0; i < classes.length; i++) {
                zip.putNextEntry(new ZipEntry("com/example/Mod" + i + ".class"));
                zip.write(classes[i]);
                zip.closeEntry();
            }
        }
        return jar;
    }

    @Test
    void jarWhoseMainClassUsesNetNeoforgedModIsSkipped(@TempDir Path dir) throws IOException {
        Path path = jar(dir, "newmod-neoforge-1.21.1.jar",
                modClass("com/example/Mod0", "Lnet/neoforged/fml/common/Mod;", "newmod"));
        try (ModJar mj = ModJar.open(path)) {
            ModMetadataException e = assertThrows(ModMetadataException.class, () -> new ForgeAdapter().readMods(mj));
            assertTrue(e.isSkip());
            assertTrue(e.getMessage().contains("com.example.Mod0"), e.getMessage());
        }
    }

    @Test
    void jarWithForgeModAnnotationIsReadNormally(@TempDir Path dir) throws Exception {
        Path path = jar(dir, "newmod-forge-1.20.1.jar",
                modClass("com/example/Mod0", "Lnet/minecraftforge/fml/common/Mod;", "newmod"));
        try (ModJar mj = ModJar.open(path)) {
            List<ModCandidate> mods = new ForgeAdapter().readMods(mj);
            assertEquals(1, mods.size());
            assertEquals("newmod", mods.get(0).id());
        }
    }
}
