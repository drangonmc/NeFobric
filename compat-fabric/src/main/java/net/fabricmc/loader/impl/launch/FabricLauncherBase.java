package net.fabricmc.loader.impl.launch;

import dev.nefobric.compat.fabric.impl.NeFobricFabricLauncher;

import java.util.Map;

/** Fabric Loader 内部类的兼容实现，见 {@link FabricLauncher}。 */
public abstract class FabricLauncherBase implements FabricLauncher {
    private static final MappingConfiguration MAPPING_CONFIGURATION = new MappingConfiguration();

    protected FabricLauncherBase() {
    }

    public static Class<?> getClass(String className) throws ClassNotFoundException {
        return Class.forName(className, true, getLauncher().getTargetClassLoader());
    }

    @Override
    public MappingConfiguration getMappingConfiguration() {
        return MAPPING_CONFIGURATION;
    }

    public static FabricLauncher getLauncher() {
        return NeFobricFabricLauncher.INSTANCE;
    }

    public static Map<String, Object> getProperties() {
        return NeFobricFabricLauncher.INSTANCE.properties();
    }

    public static boolean isMixinReady() {
        return dev.nefobric.loader.impl.NeFobricLoader.isInitialized();
    }
}
