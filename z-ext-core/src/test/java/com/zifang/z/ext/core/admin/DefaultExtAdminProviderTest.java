package com.zifang.z.ext.core.admin;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.core.store.ActiveImplStore;
import com.zifang.z.ext.core.store.LocalActiveImplStore;
import com.zifang.z.ext.test.CustomOrderService;
import com.zifang.z.ext.test.DefaultOrderService;
import com.zifang.z.ext.test.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 控制面契约测试：快照 / 激活 / 停用 / 登记，以及多形态接入的可插拔性。
 */
class DefaultExtAdminProviderTest {

    private DefaultExtAdminProvider provider;

    @BeforeEach
    void setUp() {
        ExtRegistry.clear();
        provider = new DefaultExtAdminProvider("test-instance");

        ExtPointDefinition pointDef = new ExtPointDefinition();
        pointDef.setPoint("order.service");
        pointDef.setInterfaceClass(OrderService.class);
        pointDef.setType(ExtType.SYNC);
        pointDef.setDescription("订单服务");
        pointDef.setVersion("1.0.0");
        ExtRegistry.registerPoint(pointDef);

        registerImpl("default", ExtImplType.PLATFORM, DefaultOrderService.class, 1);
        registerImpl("custom", ExtImplType.CUSTOM, CustomOrderService.class, 2);
    }

    @AfterEach
    void tearDown() {
        ExtRegistry.setActiveImplStore(new LocalActiveImplStore());
        ExtRegistry.clear();
    }

    private void registerImpl(String name, ExtImplType type, Class<?> implClass, int order) {
        ExtImplDefinition def = new ExtImplDefinition();
        def.setPoint("order.service");
        def.setName(name);
        def.setType(type);
        def.setImplClass(implClass);
        def.setEnabled(true);
        def.setOrder(order);
        ExtRegistry.registerImpl(def);
    }

    @Test
    void snapshotListsPointsAndImplementations() {
        ExtSnapshot snapshot = provider.snapshot();

        assertEquals("test-instance", snapshot.getInstance());
        assertNotNull(snapshot.getTimestamp());
        assertEquals(1, snapshot.getPoints().size());

        ExtPointView pointView = snapshot.getPoints().get(0);
        assertEquals("order.service", pointView.getPoint());
        assertEquals(OrderService.class.getName(), pointView.getInterfaceClass());
        assertEquals("SYNC", pointView.getType());
        assertEquals(2, pointView.getImplementations().size());
        assertEquals("default", pointView.getImplementations().get(0).getName(), "实现应按 order 排序");
    }

    @Test
    void activateSwitchesActiveImplementation() {
        assertTrue(provider.activate("order.service", "custom"));
        assertEquals("custom", ExtRegistry.getActiveImpl("order.service"));

        ExtSnapshot snapshot = provider.snapshot();
        assertEquals("custom", snapshot.getPoints().get(0).getActiveImpl());
        assertTrue(snapshot.getPoints().get(0).getImplementations().get(1).isActive());
    }

    @Test
    void activateUnknownImplementationFails() {
        assertFalse(provider.activate("order.service", "not-exist"));
        assertNull(ExtRegistry.getActiveImpl("order.service"));
    }

    @Test
    void deactivateRemovesActivation() {
        provider.activate("order.service", "custom");
        assertTrue(provider.deactivate("order.service"));
        assertNull(ExtRegistry.getActiveImpl("order.service"));
    }

    @Test
    void deactivateUnknownPointFails() {
        assertFalse(provider.deactivate("not-exist"));
    }

    @Test
    void setEnabledTogglesImplementation() {
        assertTrue(provider.setEnabled("order.service", "custom", false));
        assertFalse(ExtRegistry.getImplementations("order.service").stream()
                .filter(i -> "custom".equals(i.getName()))
                .findFirst().orElseThrow(AssertionError::new)
                .isEnabled());

        assertTrue(provider.setEnabled("order.service", "custom", true));
    }

    @Test
    void registerImplAddsNewImplementation() {
        ExtImplDefinition def = new ExtImplDefinition();
        def.setPoint("order.service");
        def.setName("late");
        def.setType(ExtImplType.CUSTOM);
        def.setImplClass(CustomOrderService.class);
        def.setEnabled(true);

        assertTrue(provider.registerImpl(def));
        assertEquals(3, provider.snapshot().getPoints().get(0).getImplementations().size());
    }

    @Test
    void registerImplRejectsConflictingName() {
        ExtImplDefinition def = new ExtImplDefinition();
        def.setPoint("order.service");
        def.setName("custom");
        def.setType(ExtImplType.CUSTOM);
        def.setImplClass(DefaultOrderService.class);

        assertFalse(provider.registerImpl(def), "同名但实现类不同应被判为冲突");
    }

    @Test
    void registerImplIsIdempotentForSameNameAndClass() {
        ExtImplDefinition def = new ExtImplDefinition();
        def.setPoint("order.service");
        def.setName("custom");
        def.setType(ExtImplType.CUSTOM);
        def.setImplClass(CustomOrderService.class);

        assertTrue(provider.registerImpl(def), "同名同类重复登记应幂等放行");
        assertEquals(2, provider.snapshot().getPoints().get(0).getImplementations().size());
    }

    @Test
    void activeImplStoreIsPluggable() {
        // 外置形态：把激活状态放到进程外，业务进程换 store 即可对齐
        Map<String, String> shared = new ConcurrentHashMap<>();
        ActiveImplStore remoteLike = new ActiveImplStore() {
            @Override
            public void setActiveImpl(String point, String implName) {
                if (implName == null) {
                    shared.remove(point);
                } else {
                    shared.put(point, implName);
                }
            }

            @Override
            public String getActiveImpl(String point) {
                return shared.get(point);
            }

            @Override
            public Map<String, String> getAll() {
                return new ConcurrentHashMap<>(shared);
            }
        };

        ExtRegistry.setActiveImplStore(remoteLike);
        assertTrue(provider.activate("order.service", "custom"));
        assertEquals("custom", shared.get("order.service"));
        assertEquals("custom", ExtRegistry.getActiveImpl("order.service"));
    }
}
