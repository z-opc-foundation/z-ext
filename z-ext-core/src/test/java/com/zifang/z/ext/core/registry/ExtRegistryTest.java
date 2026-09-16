package com.zifang.z.ext.core.registry;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.test.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExtRegistry 注册中心测试
 */
class ExtRegistryTest {

    @BeforeEach
    void setUp() {
        // 每个测试前清空注册表
        ExtRegistry.clear();
    }

    @Test
    void testRegisterPoint() {
        // 创建扩展点定义
        ExtPointDefinition pointDef = new ExtPointDefinition();
        pointDef.setPoint("order.service");
        pointDef.setInterfaceClass(OrderService.class);
        pointDef.setType(ExtType.SYNC);
        pointDef.setDescription("订单服务");
        pointDef.setVersion("1.0.0");

        // 注册扩展点
        ExtRegistry.registerPoint(pointDef);

        // 验证注册成功
        ExtPointDefinition result = ExtRegistry.getPoint("order.service");
        assertNotNull(result);
        assertEquals("order.service", result.getPoint());
        assertEquals(OrderService.class, result.getInterfaceClass());
        assertEquals(ExtType.SYNC, result.getType());
    }

    @Test
    void testRegisterImpl() {
        // 先注册扩展点
        ExtPointDefinition pointDef = new ExtPointDefinition("order.service", OrderService.class);
        ExtRegistry.registerPoint(pointDef);

        // 创建实现定义
        ExtImplDefinition implDef = new ExtImplDefinition();
        implDef.setPoint("order.service");
        implDef.setName("default");
        implDef.setImplClass(com.zifang.z.ext.test.DefaultOrderService.class);
        implDef.setType(ExtImplType.PLATFORM);
        implDef.setEnabled(true);
        implDef.setOrder(0);

        // 注册实现
        ExtRegistry.registerImpl(implDef);

        // 验证注册成功
        List<ExtImplDefinition> impls = ExtRegistry.getImplementations("order.service");
        assertEquals(1, impls.size());
        assertEquals("default", impls.get(0).getName());
    }

    @Test
    void testGetEnabledImplementations() {
        // 先注册扩展点
        ExtPointDefinition pointDef = new ExtPointDefinition("order.service", OrderService.class);
        ExtRegistry.registerPoint(pointDef);

        // 注册平台实现（启用）
        ExtImplDefinition platformImpl = new ExtImplDefinition();
        platformImpl.setPoint("order.service");
        platformImpl.setName("platform");
        platformImpl.setImplClass(com.zifang.z.ext.test.DefaultOrderService.class);
        platformImpl.setType(ExtImplType.PLATFORM);
        platformImpl.setEnabled(true);
        ExtRegistry.registerImpl(platformImpl);

        // 注册自定义实现（禁用）
        ExtImplDefinition customImpl = new ExtImplDefinition();
        customImpl.setPoint("order.service");
        customImpl.setName("custom");
        customImpl.setImplClass(com.zifang.z.ext.test.CustomOrderService.class);
        customImpl.setType(ExtImplType.CUSTOM);
        customImpl.setEnabled(false);
        ExtRegistry.registerImpl(customImpl);

        // 获取启用的实现
        List<ExtImplDefinition> enabledImpls = ExtRegistry.getEnabledImplementations("order.service");
        assertEquals(1, enabledImpls.size());
        assertEquals("platform", enabledImpls.get(0).getName());
    }

    @Test
    void testSetActiveImpl() {
        // 先注册扩展点和实现
        ExtPointDefinition pointDef = new ExtPointDefinition("order.service", OrderService.class);
        ExtRegistry.registerPoint(pointDef);

        ExtImplDefinition implDef = new ExtImplDefinition();
        implDef.setPoint("order.service");
        implDef.setName("default");
        implDef.setImplClass(com.zifang.z.ext.test.DefaultOrderService.class);
        implDef.setType(ExtImplType.PLATFORM);
        implDef.setEnabled(true);
        ExtRegistry.registerImpl(implDef);

        // 设置激活的实现
        ExtRegistry.setActiveImpl("order.service", "default");

        // 验证激活成功
        String activeImpl = ExtRegistry.getActiveImpl("order.service");
        assertEquals("default", activeImpl);
    }

    @Test
    void testFindImplByType() {
        // 先注册扩展点
        ExtPointDefinition pointDef = new ExtPointDefinition("order.service", OrderService.class);
        ExtRegistry.registerPoint(pointDef);

        // 注册平台实现
        ExtImplDefinition platformImpl = new ExtImplDefinition();
        platformImpl.setPoint("order.service");
        platformImpl.setName("platform");
        platformImpl.setImplClass(com.zifang.z.ext.test.DefaultOrderService.class);
        platformImpl.setType(ExtImplType.PLATFORM);
        platformImpl.setEnabled(true);
        ExtRegistry.registerImpl(platformImpl);

        // 注册自定义实现
        ExtImplDefinition customImpl = new ExtImplDefinition();
        customImpl.setPoint("order.service");
        customImpl.setName("custom");
        customImpl.setImplClass(com.zifang.z.ext.test.CustomOrderService.class);
        customImpl.setType(ExtImplType.CUSTOM);
        customImpl.setEnabled(true);
        ExtRegistry.registerImpl(customImpl);

        // 测试按类型查找
        ExtImplDefinition platform = ExtRegistry.findImplByType("order.service", ExtImplType.PLATFORM);
        assertNotNull(platform);
        assertEquals("platform", platform.getName());

        ExtImplDefinition custom = ExtRegistry.findImplByType("order.service", ExtImplType.CUSTOM);
        assertNotNull(custom);
        assertEquals("custom", custom.getName());

        ExtImplDefinition external = ExtRegistry.findImplByType("order.service", ExtImplType.EXTERNAL);
        assertNull(external);
    }

    @Test
    void testGetPlatformImpl() {
        // 先注册扩展点
        ExtPointDefinition pointDef = new ExtPointDefinition("order.service", OrderService.class);
        ExtRegistry.registerPoint(pointDef);

        // 注册平台实现
        ExtImplDefinition platformImpl = new ExtImplDefinition();
        platformImpl.setPoint("order.service");
        platformImpl.setName("default");
        platformImpl.setImplClass(com.zifang.z.ext.test.DefaultOrderService.class);
        platformImpl.setType(ExtImplType.PLATFORM);
        platformImpl.setEnabled(true);
        ExtRegistry.registerImpl(platformImpl);

        // 测试获取平台实现
        ExtImplDefinition result = ExtRegistry.getPlatformImpl("order.service");
        assertNotNull(result);
        assertEquals(ExtImplType.PLATFORM, result.getType());
    }

    @Test
    void testGetPointNotFound() {
        ExtPointDefinition result = ExtRegistry.getPoint("non.existent");
        assertNull(result);
    }

    @Test
    void testGetImplementationsNotFound() {
        List<ExtImplDefinition> result = ExtRegistry.getImplementations("non.existent");
        assertTrue(result.isEmpty());
    }

    @Test
    void testClear() {
        // 注册一些数据
        ExtPointDefinition pointDef = new ExtPointDefinition("order.service", OrderService.class);
        ExtRegistry.registerPoint(pointDef);

        ExtImplDefinition implDef = new ExtImplDefinition();
        implDef.setPoint("order.service");
        implDef.setName("default");
        implDef.setImplClass(com.zifang.z.ext.test.DefaultOrderService.class);
        implDef.setEnabled(true);
        ExtRegistry.registerImpl(implDef);

        // 清空
        ExtRegistry.clear();

        // 验证清空成功
        assertNull(ExtRegistry.getPoint("order.service"));
        assertTrue(ExtRegistry.getImplementations("order.service").isEmpty());
    }
}