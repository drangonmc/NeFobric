package dev.nefobric.compat.forge;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlParser;
import dev.nefobric.loader.metadata.Dependency;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.ModMetadataException;
import dev.nefobric.loader.metadata.SideRestriction;
import dev.nefobric.loader.util.Messages;
import dev.nefobric.loader.version.MavenVersionRange;
import dev.nefobric.loader.version.VersionPredicate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 解析 Forge 47 / NeoForge 47.1 的 {@code META-INF/mods.toml}。一个文件可以声明多个模组；
 * 只要有任何一条依赖指向 {@code neoforge}，就把这个文件里的模组都标成 NeoForge 模组。
 */
public final class ModsTomlParser {
    public static final String PATH = "META-INF/mods.toml";
    /** 存放 @Mod 主类名的附加字段。 */
    public static final String EXTRA_MOD_CLASS = "forge.modClass";
    public static final String EXTRA_MOD_LOADER = "forge.modLoader";

    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_]{1,63}");

    /** 解析时需要的 jar 信息。 */
    public record JarInfo(Path origin, String implementationVersion, Map<String, String> modClasses,
                          List<String> manifestMixins, boolean hasAccessTransformer, boolean hasJarJar) {
    }

    private ModsTomlParser() {
    }

    public static List<ModCandidate> parse(String toml, JarInfo jar) throws ModMetadataException {
        CommentedConfig cfg;
        try {
            cfg = new TomlParser().parse(toml);
        } catch (ParsingException e) {
            throw new ModMetadataException(Messages.tr("forge.badToml", e.getMessage()), e);
        }
        try {
            return parse(cfg, jar);
        } catch (ClassCastException e) {
            throw new ModMetadataException(Messages.tr("forge.badToml", e.getMessage()), e);
        }
    }

    private static List<ModCandidate> parse(Config cfg, JarInfo jar) throws ModMetadataException {
        String modLoader = string(cfg, "modLoader", "javafml");
        if (!modLoader.equals("javafml") && !modLoader.equals("lowcodefml")) {
            throw new ModMetadataException(Messages.tr("forge.unsupportedLoader", modLoader));
        }
        String loaderVersion = string(cfg, "loaderVersion", "");
        List<Config> mods = tables(cfg.get("mods"));
        if (mods.isEmpty()) {
            throw new ModMetadataException(Messages.tr("forge.noMods"));
        }
        Object depsRoot = cfg.get("dependencies");
        Config deps = depsRoot instanceof Config c ? c : null;
        boolean neo = mentionsNeoForge(deps);

        List<String> mixins = new ArrayList<>(jar.manifestMixins());
        for (Config m : tables(cfg.get("mixins"))) {
            Object config = m.get("config");
            if (config instanceof String s && !mixins.contains(s)) {
                mixins.add(s);
            }
        }

        List<ModCandidate> out = new ArrayList<>();
        for (Config m : mods) {
            String modId = string(m, "modId", null);
            if (modId == null || !MOD_ID.matcher(modId).matches()) {
                throw new ModMetadataException(Messages.tr("forge.badId", String.valueOf(modId)));
            }
            String version = string(m, "version", "1");
            if (version.contains("${file.jarVersion}")) {
                version = version.replace("${file.jarVersion}",
                        jar.implementationVersion() == null ? "0.0NONE" : jar.implementationVersion());
            }
            ModCandidate.Builder b = ModCandidate.builder(modId, version, neo ? Ecosystem.NEOFORGE : Ecosystem.FORGE)
                    .origin(jar.origin())
                    .name(string(m, "displayName", modId))
                    .extra(EXTRA_MOD_LOADER, modLoader);
            String modClass = jar.modClasses().get(modId);
            if (modClass != null) {
                b.extra(EXTRA_MOD_CLASS, modClass);
            }
            if (!loaderVersion.isBlank()) {
                b.dependency(Dependency.of(modLoader, Dependency.Kind.REQUIRED, range(modId, modLoader, loaderVersion)));
            }
            if (deps != null) {
                Object list = deps.get(List.of(modId));
                for (Config d : tables(list)) {
                    b.dependency(parseDependency(modId, d));
                }
            }
            mixins.forEach(b::mixinConfig);
            if (jar.hasAccessTransformer()) {
                b.accessTransformer("META-INF/accesstransformer.cfg");
            }
            if (jar.hasJarJar()) {
                b.nestedJar("META-INF/jarjar/metadata.json");
            }
            out.add(b.build());
        }
        return out;
    }

    private static Dependency parseDependency(String owner, Config d) throws ModMetadataException {
        String target = string(d, "modId", null);
        if (target == null || target.isBlank()) {
            throw new ModMetadataException(Messages.tr("forge.badDependency", owner, "modId"));
        }
        Dependency.Kind kind;
        Object type = d.get("type");
        if (type instanceof String s) {
            switch (s.toLowerCase(Locale.ROOT)) {
                case "required":
                    kind = Dependency.Kind.REQUIRED;
                    break;
                case "optional":
                    kind = Dependency.Kind.OPTIONAL;
                    break;
                case "incompatible":
                    kind = Dependency.Kind.INCOMPATIBLE;
                    break;
                case "discouraged":
                    kind = Dependency.Kind.DISCOURAGED;
                    break;
                default:
                    throw new ModMetadataException(Messages.tr("forge.badDependency", owner, "type=" + s));
            }
        } else {
            Object mandatory = d.get("mandatory");
            kind = Boolean.FALSE.equals(mandatory) ? Dependency.Kind.OPTIONAL : Dependency.Kind.REQUIRED;
        }
        VersionPredicate predicate = range(owner, target, string(d, "versionRange", ""));
        Dependency.Ordering ordering;
        switch (string(d, "ordering", "NONE").toUpperCase(Locale.ROOT)) {
            case "BEFORE":
                ordering = Dependency.Ordering.BEFORE;
                break;
            case "AFTER":
                ordering = Dependency.Ordering.AFTER;
                break;
            default:
                ordering = Dependency.Ordering.NONE;
                break;
        }
        SideRestriction side;
        switch (string(d, "side", "BOTH").toUpperCase(Locale.ROOT)) {
            case "CLIENT":
                side = SideRestriction.CLIENT;
                break;
            case "SERVER":
                side = SideRestriction.SERVER;
                break;
            default:
                side = SideRestriction.ANY;
                break;
        }
        return new Dependency(target, kind, predicate, side, ordering);
    }

    private static VersionPredicate range(String owner, String target, String spec) throws ModMetadataException {
        try {
            return MavenVersionRange.parse(spec);
        } catch (IllegalArgumentException e) {
            throw new ModMetadataException(Messages.tr("forge.badDependency", owner, target + " versionRange=" + spec), e);
        }
    }

    private static boolean mentionsNeoForge(Config deps) {
        if (deps == null) {
            return false;
        }
        for (Object value : deps.valueMap().values()) {
            for (Config d : tables(value)) {
                if ("neoforge".equals(d.get("modId"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Config> tables(Object value) {
        List<Config> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Config c) {
                    out.add(c);
                }
            }
        } else if (value instanceof Config c) {
            out.add(c);
        }
        return out;
    }

    private static String string(Config cfg, String key, String fallback) {
        Object v = cfg.get(List.of(key));
        return v == null ? fallback : String.valueOf(v);
    }
}
