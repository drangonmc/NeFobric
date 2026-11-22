package dev.nefobric.forge.common;

import net.minecraftforge.common.util.Lazy;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullLazy;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LazyOptionalTest {
    @Test
    void resolvesOnceAndCaches() {
        AtomicInteger calls = new AtomicInteger();
        LazyOptional<String> lo = LazyOptional.of(() -> "v" + calls.incrementAndGet());
        assertTrue(lo.isPresent());
        assertEquals(0, calls.get());
        assertEquals("v1", lo.orElse("x"));
        assertEquals(Optional.of("v1"), lo.resolve());
        assertEquals(Optional.of(2), lo.map(String::length));
        assertEquals(1, calls.get());
    }

    @Test
    void emptyIsShared() {
        assertSame(LazyOptional.empty(), LazyOptional.<Integer>empty());
        assertSame(LazyOptional.empty(), LazyOptional.of(null));
        assertFalse(LazyOptional.empty().isPresent());
        assertEquals("x", LazyOptional.<String>empty().orElse("x"));
        AtomicInteger heard = new AtomicInteger();
        LazyOptional.empty().addListener(l -> heard.incrementAndGet());
        assertEquals(1, heard.get(), "listeners on an empty optional fire at once");
    }

    @Test
    void invalidateNotifiesListenersOnce() {
        LazyOptional<String> lo = LazyOptional.of(() -> "v");
        AtomicInteger heard = new AtomicInteger();
        lo.addListener(l -> heard.incrementAndGet());
        lo.invalidate();
        lo.invalidate();
        assertEquals(1, heard.get());
        assertFalse(lo.isPresent());
        assertEquals("x", lo.orElse("x"));
        assertThrows(IllegalStateException.class, () -> lo.orElseThrow(IllegalStateException::new));
    }

    @Test
    void nullFromSupplierIsAnError() {
        LazyOptional<String> lo = LazyOptional.of(() -> null);
        assertThrows(IllegalStateException.class, () -> lo.orElse("x"));
    }

    @Test
    void lazyMapDefersWork() {
        AtomicInteger calls = new AtomicInteger();
        LazyOptional<Integer> mapped = LazyOptional.of(() -> "abc").lazyMap(s -> {
            calls.incrementAndGet();
            return s.length();
        });
        assertEquals(0, calls.get());
        assertEquals(3, mapped.orElse(0));
        assertEquals(1, calls.get());
    }

    @Test
    void lazyComputesOnce() {
        AtomicInteger calls = new AtomicInteger();
        Lazy<Integer> fast = Lazy.of(calls::incrementAndGet);
        Lazy<Integer> concurrent = Lazy.concurrentOf(calls::incrementAndGet);
        NonNullLazy<Integer> nonNull = NonNullLazy.of(calls::incrementAndGet);
        assertEquals(fast.get(), fast.get());
        assertEquals(concurrent.get(), concurrent.get());
        assertEquals(nonNull.get(), nonNull.get());
        assertEquals(3, calls.get());
    }
}
