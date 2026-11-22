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
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 扫描模组文件夹，让每个兼容层读取自己认识的元数据。模组内嵌的 jar（Jar-in-Jar：Fabric 的 {@code META-INF/jars}、
 * Forge 的 jarjar）按内容解到缓存里，再用同样的方式读，可以多层嵌套。同一个 id 的多个版本由依赖解析挑选。
 */
public final class ModDiscoverer {
    /** 嵌套层数上限，防止构造出来的循环嵌套 jar。 */
    static final int MAX_DEPTH = 8;

    public record Result(List<ModCandidate> candidates, List<NestedLibrary> libraries, List<String> errors,
                         List<String> warnings, List<String> notes) {
        public Result(List<ModCandidate> candidates, List<String> errors, List<String> warnings, List<String> notes) {
            this(candidates, List.of(), errors, warnings, notes);
        }
    }

    private record Pending(Path path, ModCandidate parent, String pathInParent, int depth) {
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

    /** 不解内嵌 jar。 */
    public static Result read(List<Path> jars, List<EcosystemAdapter> adapters) {
        return read(jars, adapters, null);
    }

    /**
     * @param nestedCacheDir 内嵌 jar 解到这里（按内容的 sha1 分目录）；{@code null} 表示不处理内嵌 jar
     */
    public static Result read(List<Path> jars, List<EcosystemAdapter> adapters, Path nestedCacheDir) {
        List<EcosystemAdapter> ordered = new ArrayList<>(adapters);
        ordered.sort(Comparator.comparingInt(EcosystemAdapter::priority));
        List<ModCandidate> candidates = new ArrayList<>();
        List<NestedLibrary> libraries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Deque<Pending> queue = new ArrayDeque<>();
        jars.forEach(j -> queue.add(new Pending(j, null, null, 0)));
        while (!queue.isEmpty()) {
            Pending pending = queue.poll();
            Path jar = pending.path();
            String file = pending.parent() == null ? String.valueOf(jar.getFileName())
                    : pending.parent().fileName() + "!/" + pending.pathInParent();
            try (ModJar mj = ModJar.open(jar)) {
                List<EcosystemAdapter> found = new ArrayList<>();
                for (EcosystemAdapter a : ordered) {
                    if (a.hasMetadata(mj)) {
                        found.add(a);
                    }
                }
                if (found.isEmpty()) {
                    if (pending.parent() == null) {
                        explainUnknownJar(mj, warnings, notes);
                    } else {
                        libraries.add(new NestedLibrary(jar, pending.parent(), pending.pathInParent()));
                        Log.debug("Nested library " + file);
                    }
                    continue;
                }
                EcosystemAdapter chosen = found.get(0);
                if (found.size() > 1) {
                    notes.add(Messages.tr("discover.multiMetadata", file, chosen.displayName(),
                            found.get(1).displayName()));
                }
                try {
                    List<ModCandidate> mods = chosen.readMods(mj);
                    if (pending.parent() != null) {
                        mods.forEach(m -> m.linkNested(pending.parent(), pending.pathInParent()));
                    }
                    candidates.addAll(mods);
                    Log.debug("Read " + mods + " from " + file + " using " + chosen.displayName());
                    if (nestedCacheDir != null && !mods.isEmpty()) {
                        queueNested(chosen, mods, mj, pending, nestedCacheDir, queue, warnings);
                    }
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
        return new Result(candidates, libraries, errors, warnings, notes);
    }

    private static void queueNested(EcosystemAdapter adapter, List<ModCandidate> mods, ModJar jar, Pending pending,
                                    Path cacheDir, Deque<Pending> queue, List<String> warnings)
            throws IOException {
        // 一个 jar 里有多个模组（Forge 的 mods.toml 可以写多个）时，内嵌的 jar 都算第一个模组的
        ModCandidate owner = mods.get(0);
        Set<String> entries = new LinkedHashSet<>();
        for (ModCandidate m : mods) {
            entries.addAll(adapter.nestedJars(m, jar));
        }
        for (String entry : entries) {
            if (pending.depth() + 1 > MAX_DEPTH) {
                warnings.add(Messages.tr("discover.nestedTooDeep", owner.describe(), entry));
                continue;
            }
            Optional<byte[]> data = jar.read(entry);
            if (data.isEmpty()) {
                warnings.add(Messages.tr("discover.nestedMissing", owner.describe(), entry));
                continue;
            }
            // 同一个 jar 被多个模组内嵌时只解出一份，但每处都读成一个候选：依赖解析要知道它被谁内嵌，
            // 内嵌它的模组都没被选中时它也不加载
            Path out = extract(cacheDir, sha1(data.get()), entry, data.get());
            queue.add(new Pending(out, owner, entry, pending.depth() + 1));
        }
    }

    /** 按内容放进缓存：{@code <cacheDir>/<sha1 前两位>/<sha1>/<原文件名>}，已经存在就不再写。 */
    static Path extract(Path cacheDir, String sha1, String entry, byte[] data) throws IOException {
        String name = entry.substring(entry.lastIndexOf('/') + 1);
        Path dir = cacheDir.resolve(sha1.substring(0, 2)).resolve(sha1);
        Path out = dir.resolve(name);
        if (Files.isRegularFile(out) && Files.size(out) == data.length) {
            return out;
        }
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, name, ".tmp");
        try {
            Files.write(tmp, data);
            try {
                Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
        return out;
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
