package net.fabricmc.loader.api.metadata;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** fabric.mod.json 里的一条依赖。 */
public interface ModDependency {
    Kind getKind();

    String getModId();

    boolean matches(Version version);

    Collection<VersionPredicate> getVersionRequirements();

    default List<VersionInterval> getVersionIntervals() {
        List<VersionInterval> ret = Collections.emptyList();
        for (VersionPredicate predicate : getVersionRequirements()) {
            ret = VersionInterval.or(ret, predicate.getInterval());
        }
        return ret;
    }

    enum Kind {
        DEPENDS("depends", true, false),
        RECOMMENDS("recommends", true, true),
        SUGGESTS("suggests", true, true),
        CONFLICTS("conflicts", false, true),
        BREAKS("breaks", false, false);

        private static final Map<String, Kind> BY_KEY = new HashMap<>();

        static {
            for (Kind k : values()) {
                BY_KEY.put(k.key, k);
            }
        }

        private final String key;
        private final boolean positive;
        private final boolean soft;

        Kind(String key, boolean positive, boolean soft) {
            this.key = key;
            this.positive = positive;
            this.soft = soft;
        }

        public String getKey() {
            return key;
        }

        public boolean isPositive() {
            return positive;
        }

        public boolean isSoft() {
            return soft;
        }

        public static Kind parse(String key) {
            return BY_KEY.get(key);
        }
    }
}
