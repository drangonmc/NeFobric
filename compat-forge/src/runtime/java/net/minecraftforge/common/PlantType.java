package net.minecraftforge.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** 植物能长在什么地方的分类（平原、沙漠、水里……）。名字只能是小写字母和下划线。 */
public final class PlantType {
    private static final Pattern INVALID_CHARACTERS = Pattern.compile("[^a-z_]");
    private static final Map<String, PlantType> VALUES = new ConcurrentHashMap<>();

    public static final PlantType PLAINS = get("plains");
    public static final PlantType DESERT = get("desert");
    public static final PlantType BEACH = get("beach");
    public static final PlantType CAVE = get("cave");
    public static final PlantType WATER = get("water");
    public static final PlantType NETHER = get("nether");
    public static final PlantType CROP = get("crop");

    private final String name;

    private PlantType(String name) {
        this.name = name;
    }

    public static PlantType get(String name) {
        return VALUES.computeIfAbsent(name, n -> {
            if (INVALID_CHARACTERS.matcher(n).find()) {
                throw new IllegalArgumentException("PlantType.get() called with invalid name: " + name);
            }
            return new PlantType(n);
        });
    }

    public String getName() {
        return name;
    }
}
