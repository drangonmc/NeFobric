package dev.nefobric.compat.fabric.impl;

import dev.nefobric.compat.fabric.FabricAdapter;
import dev.nefobric.compat.fabric.FabricMetadataParser;
import dev.nefobric.compat.fabric.metadata.FabricModMetadata;
import dev.nefobric.compat.fabric.version.VersionParser;
import dev.nefobric.loader.impl.NeFobricLoader;
import dev.nefobric.loader.metadata.Ecosystem;
import dev.nefobric.loader.metadata.ModCandidate;
import dev.nefobric.loader.metadata.SideRestriction;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 全部模组（Fabric、Forge 系和内置模组）的 {@link ModContainer}，按加载顺序排列。
 * 与 Fabric Loader 一样，按 id 查找时 provides 里的别名也算。
 */
final class ModRegistry {
    private final List<ModContainerImpl> mods;
    private final Map<String, ModContainerImpl> byId;
    private final Map<ModCandidate, ModContainerImpl> byCandidate;

    private ModRegistry(List<ModContainerImpl> mods, Map<String, ModContainerImpl> byId,
                        Map<ModCandidate, ModContainerImpl> byCandidate) {
        this.mods = Collections.unmodifiableList(mods);
        this.byId = byId;
        this.byCandidate = byCandidate;
    }

    static ModRegistry build(NeFobricLoader loader) {
        Set<ModCandidate> ordered = new LinkedHashSet<>(loader.mods());
        ordered.addAll(loader.allModsIncludingBuiltin());
        List<ModContainerImpl> list = new ArrayList<>();
        Map<String, ModContainerImpl> byId = new LinkedHashMap<>();
        Map<ModCandidate, ModContainerImpl> byCandidate = new LinkedHashMap<>();
        for (ModCandidate c : ordered) {
            ModMetadata meta = metadataFor(c);
            List<Path> sources = codeSources(loader, c);
            ModOrigin origin;
            Optional<ModCandidate> parent = CoreBridge.parent(c);
            if (parent.isPresent()) {
                origin = ModOriginImpl.nested(parent.get().id(), CoreBridge.nestedLocation(c));
            } else if (c.origin() != null) {
                origin = ModOriginImpl.path(List.of(c.origin()));
            } else if (!sources.isEmpty()) {
                origin = ModOriginImpl.path(sources);
            } else {
                origin = ModOriginImpl.unknown();
            }
            ModContainerImpl container = new ModContainerImpl(c, meta, sources, origin);
            list.add(container);
            byCandidate.put(c, container);
            byId.putIfAbsent(c.id(), container);
        }
        // provides 的别名放在真实 id 之后，真实 id 优先
        for (ModContainerImpl container : list) {
            for (String alias : container.getMetadata().getProvides()) {
                byId.putIfAbsent(alias, container);
            }
        }
        for (ModContainerImpl container : list) {
            CoreBridge.parent(container.candidate()).map(byCandidate::get).ifPresent(p -> {
                container.setParent(p);
                p.addChild(container);
            });
        }
        return new ModRegistry(list, byId, byCandidate);
    }

    List<ModContainerImpl> all() {
        return mods;
    }

    Optional<ModContainerImpl> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    ModContainerImpl get(ModCandidate candidate) {
        return byCandidate.get(candidate);
    }

    Collection<ModContainer> asContainers() {
        return Collections.unmodifiableList(new ArrayList<>(mods));
    }

    private static ModMetadata metadataFor(ModCandidate c) {
        FabricModMetadata fabric = FabricMetadataParser.metadata(c);
        if (fabric != null) {
            return fabric;
        }
        // Forge 系模组对 Fabric 模组来说也是 “builtin”：Fabric API 的资源加载器会给每个非 builtin 模组建资源包，
        // Forge 系模组的资源由 Forge 兼容层负责，不能再被 Fabric API 加一遍（Sinytra Connector 也是这样处理的）。
        String type = "builtin";
        FabricModMetadata.Builder b = FabricModMetadata.builder(type, c.id(), VersionParser.parseLenient(c.version()))
                .name(c.name())
                .environment(toEnvironment(c.environment()));
        for (String p : c.provides()) {
            b.provides(p);
        }
        if (c.ecosystem() == Ecosystem.BUILTIN) {
            b.description(builtinDescription(c.id()));
        }
        return b.build();
    }

    private static String builtinDescription(String id) {
        switch (id) {
            case "minecraft":
                return "Minecraft";
            case "java":
                return "Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")";
            case "fabricloader":
                return "Fabric Loader API provided by NeFobric (emulating Fabric Loader "
                        + FabricAdapter.EMULATED_FABRIC_LOADER_VERSION + ")";
            default:
                return "Provided by NeFobric";
        }
    }

    private static ModEnvironment toEnvironment(SideRestriction side) {
        switch (side) {
            case CLIENT:
                return ModEnvironment.CLIENT;
            case SERVER:
                return ModEnvironment.SERVER;
            default:
                return ModEnvironment.UNIVERSAL;
        }
    }

    private static List<Path> codeSources(NeFobricLoader loader, ModCandidate c) {
        if (c.ecosystem() != Ecosystem.BUILTIN) {
            return CoreBridge.rootJars(c);
        }
        switch (c.id()) {
            case "minecraft":
                return List.of(loader.game().gameJar());
            case "java":
                return List.of(Paths.get(System.getProperty("java.home")));
            default:
                Path own = ownJar();
                return own == null ? List.of() : List.of(own);
        }
    }

    private static Path ownJar() {
        try {
            CodeSource cs = ModRegistry.class.getProtectionDomain().getCodeSource();
            return cs == null ? null : Paths.get(cs.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }
}
