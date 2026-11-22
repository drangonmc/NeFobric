package net.minecraftforge.common.capabilities;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

import java.util.Objects;

/** 模组总线事件：在这里注册自己的能力接口。 */
public final class RegisterCapabilitiesEvent extends Event implements IModBusEvent {
    public <T> void register(Class<T> type) {
        Objects.requireNonNull(type, "Attempted to register a capability with invalid type");
        CapabilityManager.INSTANCE.get(org.objectweb.asm.Type.getInternalName(type), true);
    }
}
