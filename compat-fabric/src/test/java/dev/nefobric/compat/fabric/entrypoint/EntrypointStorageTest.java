package dev.nefobric.compat.fabric.entrypoint;

import dev.nefobric.compat.fabric.FabricMetadataParser;
import dev.nefobric.compat.fabric.impl.ModContainerImpl;
import dev.nefobric.compat.fabric.impl.ModOriginImpl;
import dev.nefobric.loader.metadata.ModCandidate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntrypointStorageTest {
    private static final String P = "dev.nefobric.compat.fabric.entrypoint.Entrypoints$";

    private static ModContainerImpl mod(String json) throws Exception {
        ModCandidate c = FabricMetadataParser.parse(json, Paths.get("x.jar"));
        return new ModContainerImpl(c, FabricMetadataParser.metadata(c), List.of(), ModOriginImpl.path(List.of(Paths.get("x.jar"))));
    }

    @BeforeEach
    void reset() {
        Entrypoints.CALLS.clear();
        Entrypoints.Both.constructed = 0;
    }

    @Test
    void sameClassUnderTwoKeysIsTwoInstancesAndOrderIsKept() throws Exception {
        ModContainerImpl a = mod("{\"schemaVersion\":1,\"id\":\"aa\",\"version\":\"1\",\"entrypoints\":{\"main\":[\"" + P + "Both\"],\"client\":[\"" + P + "Both\"]}}");
        ModContainerImpl b = mod("{\"schemaVersion\":1,\"id\":\"bb\",\"version\":\"1\",\"entrypoints\":{\"main\":[\"" + P + "Holder::FIELD\"]}}");
        EntrypointStorage s = EntrypointStorage.create(List.of(a, b), getClass().getClassLoader());
        s.invoke("main", ModInitializer.class, ModInitializer::onInitialize);
        s.invoke("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
        assertEquals(List.of("both.main", "field", "both.client"), Entrypoints.CALLS);
        assertEquals(2, Entrypoints.Both.constructed);
        // 同一条入口按类型缓存：再取一次不会重新构造
        s.getEntrypoints("main", ModInitializer.class);
        assertEquals(2, Entrypoints.Both.constructed);
        List<EntrypointContainer<ModInitializer>> containers = s.getEntrypointContainers("main", ModInitializer.class);
        assertEquals("aa", containers.get(0).getProvider().getMetadata().getId());
        assertEquals(P + "Both", containers.get(0).getDefinition());
        assertTrue(s.getEntrypoints("nothing", ModInitializer.class).isEmpty());
    }

    @Test
    void failureDoesNotStopOtherModsAndIsReported() throws Exception {
        ModContainerImpl a = mod("{\"schemaVersion\":1,\"id\":\"aa\",\"version\":\"1\",\"entrypoints\":{\"main\":[\"" + P + "Fails\"]}}");
        ModContainerImpl b = mod("{\"schemaVersion\":1,\"id\":\"bb\",\"version\":\"1\",\"entrypoints\":{\"main\":[\"" + P + "Both\", \"no.such.Clazz\"]}}");
        EntrypointStorage s = EntrypointStorage.create(List.of(a, b), getClass().getClassLoader());
        List<EntrypointStorage.Failure> failures = s.invokeCollecting("main", ModInitializer.class, ModInitializer::onInitialize);
        assertEquals(List.of("both.main"), Entrypoints.CALLS);
        assertEquals(2, failures.size());
        assertEquals("aa", failures.get(0).mod().getMetadata().getId());
        assertEquals("boom", failures.get(0).error().getMessage());
        assertTrue(failures.get(1).error() instanceof ClassNotFoundException);
        EntrypointException ex = assertThrows(EntrypointException.class,
                () -> s.invoke("main", ModInitializer.class, ModInitializer::onInitialize));
        assertEquals("main", ex.getKey());
    }

    @Test
    void defaultAdapterHandlesFieldsAndMethodsLikeFabric() throws Exception {
        LanguageAdapter adapter = LanguageAdapter.getDefault();
        adapter.create(null, P + "Holder::FIELD", ModInitializer.class).onInitialize();
        adapter.create(null, P + "Holder::notFinal", ModInitializer.class).onInitialize();
        adapter.create(null, P + "Holder::staticMethod", ModInitializer.class).onInitialize();
        adapter.create(null, P + "Holder::instanceMethod", ModInitializer.class).onInitialize();
        assertEquals(List.of("field", "notFinal", "staticMethod", "instanceMethod"), Entrypoints.CALLS);
        // Fabric 只接受静态字段
        assertThrows(LanguageAdapterException.class, () -> adapter.create(null, P + "Holder::instanceField", ModInitializer.class));
        assertThrows(LanguageAdapterException.class, () -> adapter.create(null, P + "Holder::overloaded", ModInitializer.class));
        assertThrows(LanguageAdapterException.class, () -> adapter.create(null, P + "Holder::missing", ModInitializer.class));
        assertThrows(LanguageAdapterException.class, () -> adapter.create(null, P + "Holder", ModInitializer.class));
        assertThrows(LanguageAdapterException.class, () -> adapter.create(null, "a::b::c", ModInitializer.class));
    }

    @Test
    void unknownAdapterIsAnError() throws Exception {
        ModContainerImpl a = mod("{\"schemaVersion\":1,\"id\":\"aa\",\"version\":\"1\",\"entrypoints\":{\"main\":[{\"adapter\":\"kotlin\",\"value\":\"x.Y\"}]}}");
        EntrypointStorage s = EntrypointStorage.create(List.of(a), getClass().getClassLoader());
        assertThrows(EntrypointException.class, () -> s.getEntrypoints("main", ModInitializer.class));
    }

    @Test
    void modProvidedLanguageAdapterIsUsed() throws Exception {
        ModContainerImpl a = mod("{\"schemaVersion\":1,\"id\":\"aa\",\"version\":\"1\","
                + "\"languageAdapters\":{\"test\":\"dev.nefobric.compat.fabric.entrypoint.EntrypointStorageTest$TestAdapter\"},"
                + "\"entrypoints\":{\"main\":[{\"adapter\":\"test\",\"value\":\"hello\"}]}}");
        EntrypointStorage s = EntrypointStorage.create(List.of(a), getClass().getClassLoader());
        s.invoke("main", ModInitializer.class, ModInitializer::onInitialize);
        assertEquals(List.of("adapter:hello"), Entrypoints.CALLS);
    }

    public static class TestAdapter implements LanguageAdapter {
        @Override
        public <T> T create(net.fabricmc.loader.api.ModContainer mod, String value, Class<T> type) {
            return type.cast((ModInitializer) () -> Entrypoints.CALLS.add("adapter:" + value));
        }
    }
}
