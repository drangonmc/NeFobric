package dev.nefobric.compat.fabric.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectShareImplTest {
    @Test
    void sharesObjectsAndNotifiesWaiters() {
        ObjectShareImpl share = new ObjectShareImpl();
        List<Object> seen = new ArrayList<>();
        share.whenAvailable("mod:key", (k, v) -> seen.add(v));
        assertNull(share.put("mod:key", "a"));
        assertEquals(List.of("a"), seen);
        assertEquals("a", share.put("mod:key", "b"));
        assertEquals("b", share.putIfAbsent("mod:key", "c"));
        share.whenAvailable("mod:key", (k, v) -> seen.add(v));
        assertEquals(List.of("a", "b"), seen);
        assertEquals("b", share.remove("mod:key"));
        assertNull(share.get("mod:key"));
        assertThrows(IllegalArgumentException.class, () -> share.get("nokey"));
        assertThrows(IllegalArgumentException.class, () -> share.get(":x"));
        assertThrows(NullPointerException.class, () -> share.put("mod:k", null));
    }
}
