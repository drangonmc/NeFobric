package dev.nefobric.forge.common;

import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityTest {
    interface Energy {
        int stored();
    }

    interface Other {
    }

    @Test
    void sameInterfaceSameCapability() {
        Capability<Energy> a = CapabilityManager.get(new CapabilityToken<>() {
        });
        Capability<Energy> b = CapabilityManager.get(new CapabilityToken<Energy>() {
        });
        assertSame(a, b);
        assertEquals("dev/nefobric/forge/common/CapabilityTest$Energy", a.getName());
    }

    @Test
    void registrationRunsListenersOnce() {
        Capability<Other> cap = CapabilityManager.get(new CapabilityToken<>() {
        });
        List<String> heard = new ArrayList<>();
        cap.addListener(c -> heard.add("before"));
        assertFalse(cap.isRegistered());
        new RegisterCapabilitiesEvent().register(Other.class);
        assertTrue(cap.isRegistered());
        cap.addListener(c -> heard.add("after"));
        assertEquals(List.of("before", "after"), heard);
        assertThrows(IllegalArgumentException.class, () -> new RegisterCapabilitiesEvent().register(Other.class));
    }

    @Test
    void orEmptyMatchesOnlyItself() {
        Capability<Energy> energy = CapabilityManager.get(new CapabilityToken<>() {
        });
        Capability<Other> other = CapabilityManager.get(new CapabilityToken<>() {
        });
        LazyOptional<Energy> inst = LazyOptional.of(() -> () -> 5);
        assertEquals(5, energy.orEmpty(energy, inst).map(Energy::stored).orElse(0));
        assertFalse(energy.orEmpty(other, inst).isPresent());
    }

    @Test
    void toolActionsAreInterned() {
        assertSame(ToolActions.AXE_STRIP, ToolAction.get("axe_strip"));
        assertTrue(ToolActions.DEFAULT_AXE_ACTIONS.contains(ToolAction.get("axe_dig")));
    }
}
