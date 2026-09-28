package com.zifang.z.ext.core.router;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 扩展路由器测试
 */
class ExtRouterTest {

    private ExtPointDefinition pointDefSync;
    private ExtPointDefinition pointDefChain;
    private List<ExtImplDefinition> impls;

    @BeforeEach
    void setUp() {
        // 创建同步类型的扩展点
        pointDefSync = new ExtPointDefinition();
        pointDefSync.setPoint("order.service");
        pointDefSync.setType(ExtType.SYNC);

        // 创建链式类型的扩展点
        pointDefChain = new ExtPointDefinition();
        pointDefChain.setPoint("log.service");
        pointDefChain.setType(ExtType.CHAIN);

        // 创建实现列表
        impls = new ArrayList<>();

        // 添加平台实现
        ExtImplDefinition platformImpl = new ExtImplDefinition();
        platformImpl.setPoint("order.service");
        platformImpl.setName("platform");
        platformImpl.setType(ExtImplType.PLATFORM);
        platformImpl.setEnabled(true);
        platformImpl.setOrder(2);
        impls.add(platformImpl);

        // 添加外部实现
        ExtImplDefinition externalImpl = new ExtImplDefinition();
        externalImpl.setPoint("order.service");
        externalImpl.setName("external");
        externalImpl.setType(ExtImplType.EXTERNAL);
        externalImpl.setEnabled(true);
        externalImpl.setOrder(1);
        impls.add(externalImpl);

        // 添加自定义实现
        ExtImplDefinition customImpl = new ExtImplDefinition();
        customImpl.setPoint("order.service");
        customImpl.setName("custom");
        customImpl.setType(ExtImplType.CUSTOM);
        customImpl.setEnabled(true);
        customImpl.setOrder(3);
        impls.add(customImpl);
    }

    @Test
    void testSyncRoute_PriorityExternal() {
        DefaultExtRouter router = new DefaultExtRouter();
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001", 1});

        List<ExtImplDefinition> result = router.route(pointDefSync, impls, context);

        // SYNC类型只返回一个实现
        assertEquals(1, result.size());
        // 外部实现优先
        assertEquals("external", result.get(0).getName());
    }

    @Test
    void testSyncRoute_NoExternalFallbackToCustom() {
        // 移除外部实现
        impls.removeIf(i -> i.getType() == ExtImplType.EXTERNAL);

        DefaultExtRouter router = new DefaultExtRouter();
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001", 1});

        List<ExtImplDefinition> result = router.route(pointDefSync, impls, context);

        assertEquals(1, result.size());
        // 回退到自定义实现
        assertEquals("custom", result.get(0).getName());
    }

    @Test
    void testSyncRoute_OnlyPlatform() {
        // 只保留平台实现
        impls.removeIf(i -> i.getType() != ExtImplType.PLATFORM);

        DefaultExtRouter router = new DefaultExtRouter();
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001", 1});

        List<ExtImplDefinition> result = router.route(pointDefSync, impls, context);

        assertEquals(1, result.size());
        assertEquals("platform", result.get(0).getName());
    }

    @Test
    void testChainRoute_ReturnsAllImplementations() {
        // 添加更多实现到链式扩展点
        pointDefChain.setType(ExtType.CHAIN);

        List<ExtImplDefinition> chainImpls = new ArrayList<>();

        ExtImplDefinition impl1 = new ExtImplDefinition();
        impl1.setPoint("log.service");
        impl1.setName("file");
        impl1.setType(ExtImplType.PLATFORM);
        impl1.setEnabled(true);
        impl1.setOrder(1);
        chainImpls.add(impl1);

        ExtImplDefinition impl2 = new ExtImplDefinition();
        impl2.setPoint("log.service");
        impl2.setName("db");
        impl2.setType(ExtImplType.CUSTOM);
        impl2.setEnabled(true);
        impl2.setOrder(2);
        chainImpls.add(impl2);

        DefaultExtRouter router = new DefaultExtRouter();
        ExtRouterContext context = new ExtRouterContext("log", new Object[]{"info", "test message"});

        List<ExtImplDefinition> result = router.route(pointDefChain, chainImpls, context);

        // CHAIN类型返回所有实现
        assertEquals(2, result.size());
        // 按order排序
        assertEquals("file", result.get(0).getName());
        assertEquals("db", result.get(1).getName());
    }

    @Test
    void testRoute_EmptyImpls() {
        DefaultExtRouter router = new DefaultExtRouter();
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001", 1});

        List<ExtImplDefinition> result = router.route(pointDefSync, new ArrayList<>(), context);

        assertTrue(result.isEmpty());
    }

    @Test
    void testRoute_NullImpls() {
        DefaultExtRouter router = new DefaultExtRouter();
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001", 1});

        List<ExtImplDefinition> result = router.route(pointDefSync, null, context);

        assertTrue(result.isEmpty());
    }

    @Test
    void testRoute_DisabledImpls() {
        // 禁用所有实现
        impls.forEach(i -> i.setEnabled(false));

        DefaultExtRouter router = new DefaultExtRouter();
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001", 1});

        List<ExtImplDefinition> result = router.route(pointDefSync, impls, context);

        assertTrue(result.isEmpty());
    }

    @Test
    void testRouterContext() {
        ExtRouterContext context = new ExtRouterContext("createOrder", new Object[]{"P001", 1});

        assertEquals("createOrder", context.getMethodName());
        // 上一行构造的是 new Object[]{"P001", 1}，长度就是 2（对应 OrderService.createOrder(String, int)
        // 的两个入参，ExtInvocationHandler 也是按 method.getParameters() 原样透传的）。
        // 这里原先写的 1 与自己的输入矛盾，属笔误，改为 2；断言仍是精确等值，未放宽。
        assertEquals(2, context.getArgs().length);
        assertEquals("P001", context.getArgs()[0]);

        // 测试属性
        context.setAttribute("key1", "value1");
        assertEquals("value1", context.getAttribute("key1"));
        assertEquals("default", context.getAttribute("key2", "default"));
    }

    @Test
    void testRouterGetName() {
        DefaultExtRouter router = new DefaultExtRouter();
        assertEquals("default", router.getName());
    }
}