package dev.nefobric.loader.discovery;

import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.ModMetadataException;
import dev.nefobric.loader.spi.EcosystemAdapter;
import dev.nefobric.loader.util.Log;
import dev.nefobric.loader.util.Messages;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** 扫描模组文件夹，让每个兼容层读取自己认识的元数据。 */
public final class ModDiscoverer {

    public record Result(List<ModCandidate> candidates, List<String> errors, List<String> warnings, List<String> notes) {
    }

    private ModDiscoverer() {
    }

    /**
     * 列出模组文件夹里的 jar，并加上 {@code -Dnefobric.addMods=路径1;路径2}（分隔符同 classpath）指定的额外 jar 或文件夹。
     */
    public static List<Path> findJars(Path modsDir) throws IOException {
        Map<Path, Path> found = new LinkedHashMap<>();
        addFromDirectory(modsDir, found);
        String extra = System.getProperty("nefobric.addMods");
        if (extra != null && !extra.isBlank()) {
            for (String part : extra.split(File.pathSeparator)) {
                if (part.isBlank()) {
                    continue;
                }
                Path p = Paths.get(part.trim());
                if (Files.isDirectory(p)) {
                    addFromDirectory(p, found);
                } else if (Files.isRegularFile(p)) {
                    found.putIfAbsent(p.toAbsolutePath().normalize(), p);
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    private static void addFromDirectory(Path dir, Map<Path, Path> found) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> found.putIfAbsent(p.toAbsolutePath().normalize(), p.toAbsolutePath().normalize()));
        }
    }

    public static Result read(List<Path> jars, List<EcosystemAdapter> adapters) {
        List<EcosystemAdapter> ordered = new ArrayList<>(adapters);
        ordered.sort(Comparator.comparingInt(EcosystemAdapter::priority));
        List<ModCandidate> candidates = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (Path jar : jars) {
            String file = String.valueOf(jar.getFileName());
            try (ModJar mj = ModJar.open(jar)) {
                List<EcosystemAdapter> found = new ArrayList<>();
                for (EcosystemAdapter a : ordered) {
                    if (a.hasMetadata(mj)) {
                        found.add(a);
                    }
                }
                if (found.isEmpty()) {
                    explainUnknownJar(mj, warnings, notes);
                    continue;
                }
                EcosystemAdapter chosen = found.get(0);
                if (found.size() > 1) {
                    notes.add(Messages.tr("discover.multiMetadata", file, chosen.displayName(),
                            found.get(1).displayName()));
                }
                try {
                    List<ModCandidate> mods = chosen.readMods(mj);
                    candidates.addAll(mods);
                    Log.debug("Read " + mods + " from " + file + " using " + chosen.displayName());
                } catch (ModMetadataException e) {
                    if (e.isSkip()) {
                        warnings.add(Messages.tr("discover.skipped", file, e.getMessage()));
                    } else {
                        errors.add(Messages.tr("discover.badMetadata", file, e.getMessage()));
                    }
                }
            } catch (IOException | RuntimeException e) {
                errors.add(Messages.tr("discover.badJar", file, String.valueOf(e.getMessage())));
            }
        }
        return new Result(candidates, errors, warnings, notes);
    }

    private static void explainUnknownJar(ModJar jar, List<String> warnings, List<String> notes) {
        String file = jar.fileName();
        if (jar.has("quilt.mod.json")) {
            warnings.add(Messages.tr("discover.quilt", file));
        } else if (jar.has("META-INF/neoforge.mods.toml")) {
            warnings.add(Messages.tr("discover.newNeoForge", file));
        } else if (jar.has("mcmod.info")) {
            warnings.add(Messages.tr("discover.legacyForge", file));
        } else {
            notes.add(Messages.tr("discover.notAMod", file));
        }
    }
}
