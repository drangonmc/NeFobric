package net.minecraftforge.common.capabilities;

import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/**
 * 能力的登记处。按接口内部名找 {@link Capability}，第一次问到时创建；注册（{@link RegisterCapabilitiesEvent}
 * 或 {@link AutoRegisterCapability}）后回调它的监听器。
 */
public enum CapabilityManager {
    INSTANCE;

    static final Logger LOGGER = LogManager.getLogger();
    static final Type AUTO_REGISTER = Type.getType(AutoRegisterCapability.class);

    final IdentityHashMap<String, Capability<?>> providers = new IdentityHashMap<>();

    public static <T> Capability<T> get(CapabilityToken<T> type) {
        return INSTANCE.get(type.getType(), false);
    }

    @SuppressWarnings("unchecked")
    <T> Capability<T> get(String realName, boolean registering) {
        Capability<T> cap;
        synchronized (providers) {
            String key = realName.intern();
            cap = (Capability<T>) providers.computeIfAbsent(key, Capability::new);
        }
        if (registering) {
            synchronized (cap) {
                if (cap.isRegistered()) {
                    LOGGER.error("Cannot register capability implementation multiple times : {}", realName);
                    throw new IllegalArgumentException("Cannot register a capability implementation multiple times : "
                            + realName);
                }
            }
            cap.onRegister();
        }
        return cap;
    }

    /** Forge 加载状态 INJECT_CAPABILITIES：注册带 {@link AutoRegisterCapability} 的接口，再发 {@link RegisterCapabilitiesEvent}。 */
    public void injectCapabilities(List<ModFileScanData> data) {
        RegisterCapabilitiesEvent event = new RegisterCapabilitiesEvent();
        data.stream().filter(Objects::nonNull).flatMap(d -> d.getAnnotations().stream())
                .filter(a -> AUTO_REGISTER.equals(a.annotationType()))
                .map(a -> a.clazz().getInternalName())
                .distinct()
                .forEach(name -> {
                    Capability<?> cap = get(name, false);
                    if (!cap.isRegistered()) {
                        get(name, true);
                    }
                });
        ModLoader.get().postEvent(event);
    }
}
