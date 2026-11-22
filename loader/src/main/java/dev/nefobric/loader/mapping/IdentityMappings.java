package dev.nefobric.loader.mapping;

import java.util.List;

final class IdentityMappings implements Mappings {
    static final IdentityMappings INSTANCE = new IdentityMappings();

    private IdentityMappings() {
    }

    @Override
    public String runtimeNamespace() {
        return OFFICIAL;
    }

    @Override
    public List<String> namespaces() {
        return List.of(OFFICIAL);
    }

    @Override
    public boolean isIdentity() {
        return true;
    }

    @Override
    public String mapClass(String fromNs, String toNs, String className) {
        return className;
    }

    @Override
    public String mapField(String fromNs, String toNs, String owner, String name, String desc) {
        return name;
    }

    @Override
    public String mapMethod(String fromNs, String toNs, String owner, String name, String desc) {
        return name;
    }
}
