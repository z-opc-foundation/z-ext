package com.zifang.z.ext.core.router;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.context.ExtContextHolder;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 条件路由与权重分流测试。
 */
class ExtConditionRoutingTest {

    private ExtPointDefinition pointDef;
    private final DefaultExtRouter router = new DefaultExtRouter();

    @BeforeEach
    void setUp() {
        ExtRegistry.clear();
        pointDef = new ExtPointDefinition();
        pointDef.setPoint("order.service");
        pointDef.setType(ExtType.SYNC);
    }

    @AfterEach
    void tearDown() {
        ExtContextHolder.clear();
        ExtRegistry.clear();
    }

    private ExtImplDefinition impl(String name, ExtImplType type, int order, int weight, String condition) {
        ExtImplDefinition def = new ExtImplDefinition();
        def.setPoint("order.service");
        def.setName(name);
        def.setType(type);
        def.setOrder(order);
        def.setWeight(weight);
        def.setCondition(condition);
        def.setEnabled(true);
        return def;
    }

    private List<ExtImplDefinition> route(List<ExtImplDefinition> impls, ExtRouterContext context) {
        return router.route(pointDef, impls, context);
    }

    @Test
    void emptyConditionAlwaysMatches() {
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001"});
        List<ExtImplDefinition> routed = route(
                java.util.Collections.singletonList(impl("a", ExtImplType.CUSTOM, 1, 100, "")), context);
        assertEquals(1, routed.size());
    }

    @Test
    void nullConditionAlwaysMatches() {
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001"});
        List<ExtImplDefinition> routed = route(
                java.util.Collections.singletonList(impl("a", ExtImplType.CUSTOM, 1, 100, null)), context);
        assertEquals(1, routed.size());
    }

    @Test
    void conditionOnContextAttribute() {
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001"});
        context.setAttribute("tenantId", "vip");

        List<ExtImplDefinition> matched = route(
                java.util.Collections.singletonList(impl("a", ExtImplType.CUSTOM, 1, 100, "#tenantId == 'vip'")),
                context);
        assertEquals(1, matched.size());

        List<ExtImplDefinition> notMatched = route(
                java.util.Collections.singletonList(impl("a", ExtImplType.CUSTOM, 1, 100, "#tenantId == 'other'")),
                context);
        assertTrue(notMatched.isEmpty());
    }

    @Test
    void templateFormIsSupported() {
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001"});
        context.setAttribute("appEnv", "prod");

        List<ExtImplDefinition> matched = route(
                java.util.Collections.singletonList(
                        impl("a", ExtImplType.CUSTOM, 1, 100, "#{#appEnv == 'prod'}")),
                context);
        assertEquals(1, matched.size());
    }

    @Test
    void brokenExpressionIsTreatedAsNoMatch() {
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001"});
        List<ExtImplDefinition> routed = route(
                java.util.Collections.singletonList(impl("a", ExtImplType.CUSTOM, 1, 100, "#tenantId ==")), context);
        assertTrue(routed.isEmpty(), "求值失败应判为不命中，而不是默认放行");
    }

    @Test
    void chainTypeAlsoFiltersByCondition() {
        pointDef.setType(ExtType.CHAIN);
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001"});
        context.setAttribute("tenantId", "vip");

        List<ExtImplDefinition> impls = new ArrayList<>();
        impls.add(impl("vip-only", ExtImplType.CUSTOM, 1, 100, "#tenantId == 'vip'"));
        impls.add(impl("always", ExtImplType.PLATFORM, 2, 100, ""));

        List<ExtImplDefinition> routed = route(impls, context);
        assertEquals(2, routed.size());

        context.setAttribute("tenantId", "other");
        List<ExtImplDefinition> routed2 = route(impls, context);
        assertEquals(1, routed2.size());
        assertEquals("always", routed2.get(0).getName());
    }

    @Test
    void orderWinsOverWeight() {
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001"});

        List<ExtImplDefinition> impls = new ArrayList<>();
        impls.add(impl("low-order", ExtImplType.CUSTOM, 1, 1, ""));
        impls.add(impl("high-weight", ExtImplType.CUSTOM, 2, 1000, ""));

        List<ExtImplDefinition> routed = route(impls, context);
        assertEquals(1, routed.size());
        assertEquals("low-order", routed.get(0).getName(), "order 优先于权重");
    }

    @Test
    void weightSplitsWithinSameOrder() {
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001"});

        List<ExtImplDefinition> impls = new ArrayList<>();
        impls.add(impl("heavy", ExtImplType.CUSTOM, 1, 100, ""));
        impls.add(impl("light", ExtImplType.CUSTOM, 1, 1, ""));

        int heavyHits = 0;
        int rounds = 200;
        for (int i = 0; i < rounds; i++) {
            List<ExtImplDefinition> routed = route(impls, context);
            assertEquals(1, routed.size());
            if ("heavy".equals(routed.get(0).getName())) {
                heavyHits++;
            }
        }
        // 100:1 的权重比下，200 次里 heavy 至少命中 ~180 次；阈值留足余量避免偶发抖动
        assertTrue(heavyHits > 180, "权重分流应按 100:1 倾斜，实际命中 " + heavyHits);
    }

    @Test
    void zeroWeightIsTreatedAsOne() {
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001"});

        List<ExtImplDefinition> impls = new ArrayList<>();
        impls.add(impl("only", ExtImplType.CUSTOM, 1, 100, ""));
        impls.add(impl("other", ExtImplType.CUSTOM, 1, 0, ""));

        for (int i = 0; i < 50; i++) {
            List<ExtImplDefinition> routed = route(impls, context);
            assertNotNull(routed);
            assertEquals(1, routed.size());
        }
    }

    @Test
    void disabledImplementationIsNotSelected() {
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001"});

        ExtImplDefinition disabled = impl("disabled", ExtImplType.CUSTOM, 1, 100, "");
        disabled.setEnabled(false);
        List<ExtImplDefinition> impls = new ArrayList<>();
        impls.add(disabled);
        impls.add(impl("enabled", ExtImplType.PLATFORM, 2, 100, ""));

        List<ExtImplDefinition> routed = route(impls, context);
        assertEquals(1, routed.size());
        assertEquals("enabled", routed.get(0).getName());
    }

    @Test
    void threadContextAttributesAreVisibleToCondition() {
        ExtContextHolder.set("tenantId", "vip");
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001"});
        context.getAttributes().putAll(ExtContextHolder.getAll());

        List<ExtImplDefinition> matched = route(
                java.util.Collections.singletonList(impl("a", ExtImplType.CUSTOM, 1, 100, "#tenantId == 'vip'")),
                context);
        assertEquals(1, matched.size());
    }
}
