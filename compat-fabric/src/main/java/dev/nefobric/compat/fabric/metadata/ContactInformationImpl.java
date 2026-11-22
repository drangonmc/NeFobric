package dev.nefobric.compat.fabric.metadata;

import net.fabricmc.loader.api.metadata.ContactInformation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ContactInformationImpl implements ContactInformation {
    private final Map<String, String> map;

    public ContactInformationImpl(Map<String, String> map) {
        this.map = Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(map.get(key));
    }

    @Override
    public Map<String, String> asMap() {
        return map;
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
