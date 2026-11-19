package com.zifang.z.ext.core.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地激活存储测试
 */
class LocalActiveImplStoreTest {

    private LocalActiveImplStore store;

    @BeforeEach
    void setUp() {
        store = new LocalActiveImplStore();
    }

    @Test
    void setAndGet() {
        store.setActiveImpl("order.service", "custom");
        assertEquals("custom", store.getActiveImpl("order.service"));
    }

    @Test
    void unsetFallsBackToNull() {
        store.setActiveImpl("order.service", "custom");
        store.setActiveImpl("order.service", null);
        assertNull(store.getActiveImpl("order.service"));
    }

    @Test
    void removeActiveImpl() {
        store.setActiveImpl("order.service", "custom");
        store.removeActiveImpl("order.service");
        assertNull(store.getActiveImpl("order.service"));
    }

    @Test
    void getAllReturnsSnapshot() {
        store.setActiveImpl("a", "x");
        store.setActiveImpl("b", "y");

        Map<String, String> all = store.getAll();
        assertEquals(2, all.size());

        all.clear();
        assertEquals(2, store.getAll().size(), "getAll 应返回副本，改动不得影响内部状态");
    }

    @Test
    void clearRemovesEverything() {
        store.setActiveImpl("a", "x");
        store.clear();
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void nullPointRejected() {
        assertThrows(IllegalArgumentException.class, () -> store.setActiveImpl(null, "x"));
    }
}
