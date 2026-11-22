package net.fabricmc.loader.api.metadata;

import net.fabricmc.loader.api.Version;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** 模组元数据（fabric.mod.json 的内容）。Forge 系模组和内置模组也有一份，由 NeFobric 从它们的元数据转换而来。 */
public interface ModMetadata {
    /** “fabric”、“builtin”，Forge 系模组为 “forge” 或 “neoforge”。 */
    String getType();

    String getId();

    Collection<String> getProvides();

    Version getVersion();

    ModEnvironment getEnvironment();

    Collection<ModDependency> getDependencies();

    @Deprecated
    default Collection<ModDependency> getDepends() {
        return getDependencies().stream().filter(d -> d.getKind() == ModDependency.Kind.DEPENDS).collect(Collectors.toList());
    }

    @Deprecated
    default Collection<ModDependency> getRecommends() {
        return getDependencies().stream().filter(d -> d.getKind() == ModDependency.Kind.RECOMMENDS).collect(Collectors.toList());
    }

    @Deprecated
    default Collection<ModDependency> getSuggests() {
        return getDependencies().stream().filter(d -> d.getKind() == ModDependency.Kind.SUGGESTS).collect(Collectors.toList());
    }

    @Deprecated
    default Collection<ModDependency> getConflicts() {
        return getDependencies().stream().filter(d -> d.getKind() == ModDependency.Kind.CONFLICTS).collect(Collectors.toList());
    }

    @Deprecated
    default Collection<ModDependency> getBreaks() {
        return getDependencies().stream().filter(d -> d.getKind() == ModDependency.Kind.BREAKS).collect(Collectors.toList());
    }

    String getName();

    String getDescription();

    Collection<Person> getAuthors();

    Collection<Person> getContributors();

    ContactInformation getContact();

    Collection<String> getLicense();

    Optional<String> getIconPath(int size);

    boolean containsCustomValue(String key);

    CustomValue getCustomValue(String key);

    Map<String, CustomValue> getCustomValues();
}
