package dev.nefobric.compat.fabric.impl;

import net.fabricmc.loader.api.metadata.ModOrigin;

import java.nio.file.Path;
import java.util.List;

public final class ModOriginImpl implements ModOrigin {
    private final Kind kind;
    private final List<Path> paths;
    private final String parentModId;
    private final String parentSubLocation;

    private ModOriginImpl(Kind kind, List<Path> paths, String parentModId, String parentSubLocation) {
        this.kind = kind;
        this.paths = paths;
        this.parentModId = parentModId;
        this.parentSubLocation = parentSubLocation;
    }

    public static ModOrigin path(List<Path> paths) {
        return new ModOriginImpl(Kind.PATH, List.copyOf(paths), null, null);
    }

    static ModOrigin nested(String parentModId, String parentSubLocation) {
        return new ModOriginImpl(Kind.NESTED, null, parentModId, parentSubLocation);
    }

    static ModOrigin unknown() {
        return new ModOriginImpl(Kind.UNKNOWN, null, null, null);
    }

    @Override
    public Kind getKind() {
        return kind;
    }

    @Override
    public List<Path> getPaths() {
        if (kind != Kind.PATH) {
            throw new UnsupportedOperationException("kind " + kind.name() + " doesn't have paths");
        }
        return paths;
    }

    @Override
    public String getParentModId() {
        if (kind != Kind.NESTED) {
            throw new UnsupportedOperationException("kind " + kind.name() + " doesn't have a parent mod");
        }
        return parentModId;
    }

    @Override
    public String getParentSubLocation() {
        if (kind != Kind.NESTED) {
            throw new UnsupportedOperationException("kind " + kind.name() + " doesn't have a parent sub-location");
        }
        return parentSubLocation;
    }

    @Override
    public String toString() {
        switch (kind) {
            case PATH:
                return paths.toString();
            case NESTED:
                return parentModId + ":" + parentSubLocation;
            default:
                return "unknown";
        }
    }
}
