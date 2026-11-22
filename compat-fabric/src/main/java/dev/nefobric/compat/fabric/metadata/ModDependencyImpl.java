package dev.nefobric.compat.fabric.metadata;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 一条依赖。版本要求写成数组时，任意一个满足即可。 */
public final class ModDependencyImpl implements ModDependency {
    private final Kind kind;
    private final String modId;
    private final List<String> matcherStrings;
    private final Collection<VersionPredicate> ranges;

    public ModDependencyImpl(Kind kind, String modId, List<String> matcherStrings) throws VersionParsingException {
        this.kind = kind;
        this.modId = modId;
        this.matcherStrings = List.copyOf(matcherStrings);
        List<VersionPredicate> parsed = new ArrayList<>(matcherStrings.size());
        for (String s : matcherStrings) {
            parsed.add(VersionPredicate.parse(s));
        }
        this.ranges = List.copyOf(parsed);
    }

    @Override
    public Kind getKind() {
        return kind;
    }

    @Override
    public String getModId() {
        return modId;
    }

    @Override
    public boolean matches(Version version) {
        for (VersionPredicate predicate : ranges) {
            if (predicate.test(version)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Collection<VersionPredicate> getVersionRequirements() {
        return ranges;
    }

    public List<String> matcherStrings() {
        return matcherStrings;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{").append(kind.getKey()).append(' ').append(modId).append(" @ [");
        for (int i = 0; i < matcherStrings.size(); i++) {
            if (i > 0) {
                sb.append(" || ");
            }
            sb.append(matcherStrings.get(i));
        }
        return sb.append("]}").toString();
    }
}
