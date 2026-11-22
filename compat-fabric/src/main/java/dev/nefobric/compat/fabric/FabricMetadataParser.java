package dev.nefobric.compat.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.nefobric.compat.fabric.metadata.ContactInformationImpl;
import dev.nefobric.compat.fabric.metadata.CustomValueImpl;
import dev.nefobric.compat.fabric.metadata.FabricModMetadata;
import dev.nefobric.compat.fabric.metadata.FabricModMetadata.EntrypointMetadata;
import dev.nefobric.compat.fabric.metadata.FabricModMetadata.MixinEntry;
import dev.nefobric.compat.fabric.metadata.ModDependencyImpl;
import dev.nefobric.compat.fabric.metadata.PersonImpl;
import dev.nefobric.compat.fabric.version.FabricPredicateAdapter;
import dev.nefobric.compat.fabric.version.VersionParser;
import dev.nefobric.loader.metadata.Dependency;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.ModMetadataException;
import dev.nefobric.loader.metadata.SideRestriction;
import dev.nefobric.loader.util.Messages;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.Person;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 解析 {@code fabric.mod.json}（schemaVersion 1）的全部字段，规则对齐 Fabric Loader 0.16：
 * id、version、environment、entrypoints（含 adapter）、jars、languageAdapters、mixins（含 environment）、accessWidener、
 * depends / recommends / suggests / conflicts / breaks、provides、name、description、authors、contributors、contact、
 * license、icon、custom。结果同时给出核心用的 {@link ModCandidate} 和 Fabric API 用的 {@link FabricModMetadata}。
 */
public final class FabricMetadataParser {
    /** {@link ModCandidate#extra} 里存放 {@link FabricModMetadata} 的键。 */
    public static final String EXTRA_METADATA = "fabric.metadata";

    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9-_]{1,63}");
    private static final List<String> DEPENDENCY_FIELDS = List.of("depends", "recommends", "suggests", "conflicts", "breaks");

    private FabricMetadataParser() {
    }

    public static FabricModMetadata metadata(ModCandidate mod) {
        return mod.extra(EXTRA_METADATA, FabricModMetadata.class);
    }

    public static ModCandidate parse(String json, Path origin) throws ModMetadataException {
        JsonObject o;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                throw new ModMetadataException(Messages.tr("fabric.badJson", "root is not an object"));
            }
            o = root.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new ModMetadataException(Messages.tr("fabric.badJson", e.getMessage()), e);
        }
        try {
            return parse(o, origin);
        } catch (IllegalStateException | ClassCastException | UnsupportedOperationException | NumberFormatException e) {
            // Gson 在类型不符（例如该写字符串的地方写了数组）时抛这些异常
            throw new ModMetadataException(Messages.tr("fabric.badJson", e.getMessage()), e);
        }
    }

    private static ModCandidate parse(JsonObject o, Path origin) throws ModMetadataException {
        int schema = o.has("schemaVersion") ? o.get("schemaVersion").getAsInt() : 0;
        if (schema != 1) {
            throw new ModMetadataException(Messages.tr("fabric.unsupportedSchema", schema));
        }
        String id = requireString(o, "id");
        if (!MOD_ID.matcher(id).matches()) {
            throw new ModMetadataException(Messages.tr("fabric.badId", id));
        }
        String versionString = requireString(o, "version");
        Version version;
        try {
            version = VersionParser.parse(versionString, false);
        } catch (VersionParsingException e) {
            throw new ModMetadataException(Messages.tr("fabric.missingField", "version"), e);
        }

        FabricModMetadata.Builder meta = FabricModMetadata.builder("fabric", id, version);
        ModEnvironment environment = parseEnvironment(o.get("environment"));
        meta.environment(environment);
        ModCandidate.Builder b = ModCandidate.builder(id, versionString, Ecosystem.FABRIC)
                .origin(origin)
                .environment(toSide(environment));

        if (o.has("entrypoints")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("entrypoints").entrySet()) {
                for (JsonElement entry : asArray(e.getValue())) {
                    EntrypointMetadata ep = parseEntrypoint(entry);
                    meta.entrypoint(e.getKey(), ep);
                    b.entrypoint(e.getKey(), ep.value());
                }
            }
        }
        if (o.has("jars")) {
            for (JsonElement j : o.getAsJsonArray("jars")) {
                String file = j.getAsJsonObject().get("file").getAsString();
                meta.jar(file);
                b.nestedJar(file);
            }
        }
        if (o.has("languageAdapters")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("languageAdapters").entrySet()) {
                meta.languageAdapter(e.getKey(), e.getValue().getAsString());
            }
        }
        if (o.has("mixins")) {
            for (JsonElement m : o.getAsJsonArray("mixins")) {
                MixinEntry entry;
                if (m.isJsonObject()) {
                    JsonObject mo = m.getAsJsonObject();
                    ModEnvironment env = parseEnvironment(mo.get("environment"));
                    entry = new MixinEntry(mo.get("config").getAsString(), toEnvType(env));
                } else {
                    entry = new MixinEntry(m.getAsString(), null);
                }
                meta.mixin(entry);
                b.mixinConfig(entry.config());
            }
        }
        if (o.has("accessWidener") && !o.get("accessWidener").isJsonNull()) {
            String aw = o.get("accessWidener").getAsString();
            meta.accessWidener(aw);
            b.accessTransformer(aw);
        }

        for (String field : DEPENDENCY_FIELDS) {
            if (!o.has(field)) {
                continue;
            }
            ModDependency.Kind kind = ModDependency.Kind.parse(field);
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(field).entrySet()) {
                List<String> matchers = new ArrayList<>();
                for (JsonElement v : asArray(e.getValue())) {
                    matchers.add(v.getAsString());
                }
                ModDependencyImpl dep;
                try {
                    dep = new ModDependencyImpl(kind, e.getKey(), matchers);
                } catch (VersionParsingException ex) {
                    throw new ModMetadataException(Messages.tr("fabric.badPredicate", e.getKey(), String.join(" || ", matchers)), ex);
                }
                meta.dependency(dep);
                b.dependency(Dependency.of(e.getKey(), toCoreKind(kind),
                        new FabricPredicateAdapter(dep, matchers.isEmpty() ? "[]" : String.join(" || ", matchers))));
            }
        }

        if (o.has("provides")) {
            for (JsonElement p : o.getAsJsonArray("provides")) {
                meta.provides(p.getAsString());
                b.provides(p.getAsString());
            }
        }
        String name = optString(o, "name");
        meta.name(name);
        b.name(name);
        meta.description(optString(o, "description"));
        for (Person p : parsePeople(o.get("authors"))) {
            meta.author(p);
        }
        for (Person p : parsePeople(o.get("contributors"))) {
            meta.contributor(p);
        }
        if (o.has("contact") && o.get("contact").isJsonObject()) {
            meta.contact(parseContact(o.getAsJsonObject("contact")));
        }
        if (o.has("license")) {
            for (JsonElement l : asArray(o.get("license"))) {
                meta.license(l.getAsString());
            }
        }
        if (o.has("icon")) {
            JsonElement icon = o.get("icon");
            if (icon.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : icon.getAsJsonObject().entrySet()) {
                    int size = Integer.parseInt(e.getKey());
                    if (size < 1) {
                        throw new ModMetadataException(Messages.tr("fabric.badJson", "icon size must be positive: " + size));
                    }
                    meta.icon(size, e.getValue().getAsString());
                }
            } else if (!icon.isJsonNull()) {
                meta.icon(icon.getAsString());
            }
        }
        if (o.has("custom") && o.get("custom").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("custom").entrySet()) {
                meta.custom(e.getKey(), CustomValueImpl.fromJson(e.getValue()));
            }
        }

        b.extra(EXTRA_METADATA, meta.build());
        return b.build();
    }

    private static EntrypointMetadata parseEntrypoint(JsonElement entry) {
        if (entry.isJsonPrimitive()) {
            return new EntrypointMetadata("default", entry.getAsString());
        }
        JsonObject obj = entry.getAsJsonObject();
        String adapter = obj.has("adapter") ? obj.get("adapter").getAsString() : "default";
        return new EntrypointMetadata(adapter, obj.get("value").getAsString());
    }

    private static List<Person> parsePeople(JsonElement e) {
        List<Person> out = new ArrayList<>();
        if (e == null || e.isJsonNull()) {
            return out;
        }
        for (JsonElement p : asArray(e)) {
            if (p.isJsonPrimitive()) {
                out.add(new PersonImpl(p.getAsString(), ContactInformation.EMPTY));
            } else if (p.isJsonObject()) {
                JsonObject po = p.getAsJsonObject();
                ContactInformation contact = po.has("contact") && po.get("contact").isJsonObject()
                        ? parseContact(po.getAsJsonObject("contact")) : ContactInformation.EMPTY;
                out.add(new PersonImpl(po.get("name").getAsString(), contact));
            }
        }
        return out;
    }

    private static ContactInformation parseContact(JsonObject o) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (e.getValue().isJsonPrimitive()) {
                map.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return new ContactInformationImpl(map);
    }

    private static Dependency.Kind toCoreKind(ModDependency.Kind kind) {
        switch (kind) {
            case DEPENDS:
                return Dependency.Kind.REQUIRED;
            case RECOMMENDS:
                return Dependency.Kind.RECOMMENDED;
            case SUGGESTS:
                return Dependency.Kind.SUGGESTED;
            case CONFLICTS:
                return Dependency.Kind.DISCOURAGED;
            case BREAKS:
            default:
                return Dependency.Kind.INCOMPATIBLE;
        }
    }

    /** 与 Fabric Loader 相同：不区分大小写，空字符串和 “*” 都表示两端。 */
    private static ModEnvironment parseEnvironment(JsonElement e) throws ModMetadataException {
        if (e == null || e.isJsonNull()) {
            return ModEnvironment.UNIVERSAL;
        }
        String s = e.getAsString().toLowerCase(Locale.ROOT);
        switch (s) {
            case "":
            case "*":
                return ModEnvironment.UNIVERSAL;
            case "client":
                return ModEnvironment.CLIENT;
            case "server":
                return ModEnvironment.SERVER;
            default:
                throw new ModMetadataException(Messages.tr("fabric.badEnvironment", e.getAsString()));
        }
    }

    static SideRestriction toSide(ModEnvironment env) {
        switch (env) {
            case CLIENT:
                return SideRestriction.CLIENT;
            case SERVER:
                return SideRestriction.SERVER;
            default:
                return SideRestriction.ANY;
        }
    }

    private static EnvType toEnvType(ModEnvironment env) {
        switch (env) {
            case CLIENT:
                return EnvType.CLIENT;
            case SERVER:
                return EnvType.SERVER;
            default:
                return null;
        }
    }

    private static List<JsonElement> asArray(JsonElement e) {
        List<JsonElement> out = new ArrayList<>();
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            a.forEach(out::add);
        } else {
            out.add(e);
        }
        return out;
    }

    private static String requireString(JsonObject o, String key) throws ModMetadataException {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
            throw new ModMetadataException(Messages.tr("fabric.missingField", key));
        }
        return e.getAsString();
    }

    private static String optString(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}
