package dev.nefobric.compat.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.nefobric.loader.metadata.Dependency;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.ModMetadataException;
import dev.nefobric.loader.metadata.SideRestriction;
import dev.nefobric.loader.util.Messages;
import dev.nefobric.loader.version.FabricVersionPredicate;
import dev.nefobric.loader.version.VersionPredicate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 解析 {@code fabric.mod.json}（schemaVersion 1）。 */
public final class FabricMetadataParser {
    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9-_]{1,63}");
    private static final Map<String, Dependency.Kind> DEPENDENCY_FIELDS = Map.of(
            "depends", Dependency.Kind.REQUIRED,
            "recommends", Dependency.Kind.RECOMMENDED,
            "suggests", Dependency.Kind.SUGGESTED,
            "breaks", Dependency.Kind.INCOMPATIBLE,
            "conflicts", Dependency.Kind.DISCOURAGED);
    private static final List<String> DEPENDENCY_ORDER = List.of("depends", "recommends", "suggests", "breaks", "conflicts");

    private FabricMetadataParser() {
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
        String version = requireString(o, "version");
        ModCandidate.Builder b = ModCandidate.builder(id, version, Ecosystem.FABRIC)
                .origin(origin)
                .name(optString(o, "name"))
                .environment(parseEnvironment(o.get("environment")));

        if (o.has("entrypoints")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("entrypoints").entrySet()) {
                for (JsonElement entry : asArray(e.getValue())) {
                    b.entrypoint(e.getKey(), parseEntrypoint(e.getKey(), entry));
                }
            }
        }

        for (String field : DEPENDENCY_ORDER) {
            if (!o.has(field)) {
                continue;
            }
            Dependency.Kind kind = DEPENDENCY_FIELDS.get(field);
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(field).entrySet()) {
                b.dependency(Dependency.of(e.getKey(), kind, parsePredicate(id, e.getKey(), e.getValue())));
            }
        }

        if (o.has("provides")) {
            for (JsonElement p : o.getAsJsonArray("provides")) {
                b.provides(p.getAsString());
            }
        }
        if (o.has("jars")) {
            for (JsonElement j : o.getAsJsonArray("jars")) {
                b.nestedJar(j.getAsJsonObject().get("file").getAsString());
            }
        }
        if (o.has("mixins")) {
            for (JsonElement m : o.getAsJsonArray("mixins")) {
                b.mixinConfig(m.isJsonObject() ? m.getAsJsonObject().get("config").getAsString() : m.getAsString());
            }
        }
        if (o.has("accessWidener")) {
            b.accessTransformer(o.get("accessWidener").getAsString());
        }
        return b.build();
    }

    private static String parseEntrypoint(String key, JsonElement entry) throws ModMetadataException {
        if (entry.isJsonPrimitive()) {
            return entry.getAsString();
        }
        JsonObject obj = entry.getAsJsonObject();
        String adapter = obj.has("adapter") ? obj.get("adapter").getAsString() : "default";
        if (!"default".equals(adapter)) {
            throw new ModMetadataException(Messages.tr("fabric.unsupportedAdapter", key, adapter));
        }
        return obj.get("value").getAsString();
    }

    private static VersionPredicate parsePredicate(String modId, String depId, JsonElement value)
            throws ModMetadataException {
        List<VersionPredicate> alternatives = new ArrayList<>();
        for (JsonElement v : asArray(value)) {
            try {
                alternatives.add(FabricVersionPredicate.parse(v.getAsString()));
            } catch (IllegalArgumentException e) {
                throw new ModMetadataException(Messages.tr("fabric.badPredicate", depId, v.getAsString()), e);
            }
        }
        return VersionPredicate.anyOf(alternatives);
    }

    private static SideRestriction parseEnvironment(JsonElement e) throws ModMetadataException {
        if (e == null || e.isJsonNull()) {
            return SideRestriction.ANY;
        }
        String s = e.getAsString();
        switch (s) {
            case "*":
                return SideRestriction.ANY;
            case "client":
                return SideRestriction.CLIENT;
            case "server":
                return SideRestriction.SERVER;
            default:
                throw new ModMetadataException(Messages.tr("fabric.badEnvironment", s));
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
