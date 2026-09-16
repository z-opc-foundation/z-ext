package com.zifang.z.ext.core.proxy;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.test.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExtProxyFactory 代理工厂测试
 */
class ExtProxyFactoryTest {

    @BeforeEach
    void setUp() {
        ExtRegistry.clear();
        setupTestData();
    }

    private void setupTestData() {
        // 注册扩展点
        ExtPointDefinition pointDef = new ExtPointDefinition();
        pointDef.setPoint("order.service");
        pointDef.setInterfaceClass(OrderService.class);
        pointDef.setType(ExtType.SYNC);
        ExtRegistry.registerPoint(pointDef);

        // 注册平台实现
        ExtImplDefinition implDef = new ExtImplDefinition();
        implDef.setPoint("order.service");
        implDef.setName("default");
        implDef.setImplClass(com.zifang.z.ext.test.DefaultOrderService.class);
        implDef.setType(ExtImplType.PLATFORM);
        implDef.setEnabled(true);
        implDef.setOrder(0);
        // 设置实例
        implDef.setInstance(new com.zifang.z.ext.test.DefaultOrderService());
        ExtRegistry.registerImpl(implDef);
    }

    @Test
    void testCreateProxyFromClass() {
        // 使用代理工厂创建代理
        OrderService proxy = ExtProxyFactory.createProxy(String.valueOf(OrderService.class));

        // 验证代理不为空
        assertNotNull(proxy);

        // 验证代理可以调用方法
        String result = proxy.createOrder("P001", 10);
        assertNotNull(result);
        assertTrue(result.contains("PLATFORM"));
        assertTrue(result.contains("P001"));
    }

    @Test
    void testCreateProxyFromPointDef() {
        ExtPointDefinition pointDef = ExtRegistry.getPoint("order.service");
        OrderService proxy = ExtProxyFactory.createProxy(pointDef);

        assertNotNull(proxy);
        String result = proxy.createOrder("P002", 5);
        assertTrue(result.contains("P002"));
    }

    @Test
    void testCreateProxyFromPoint() {
        OrderService proxy = ExtProxyFactory.createProxy("order.service");

        assertNotNull(proxy);
        String result = proxy.getOrder("O123");
        assertTrue(result.contains("O123"));
    }

    @Test
    void testCreateProxy_PointNotFound() {
        assertThrows(IllegalArgumentException.class, () -> {
            ExtProxyFactory.createProxy("non.existent");
        });
    }

    @Test
    void testProxyImplementsInterface() {
        OrderService proxy = ExtProxyFactory.createProxy(String.valueOf(OrderService.class));

        // 验证代理实现了正确的接口
        assertTrue(proxy instanceof OrderService);
    }

    @Test
    void testProxyToString() {
        OrderService proxy = ExtProxyFactory.createProxy(String.valueOf(OrderService.class));

        String toString = proxy.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("order.service"));
    }

    @Test
    void testProxyHashCode() {
        OrderService proxy1 = ExtProxyFactory.createProxy(String.valueOf(OrderService.class));
        OrderService proxy2 = ExtProxyFactory.createProxy(String.valueOf(OrderService.class));

        // 不同代理对象的hashCode可能不同（取决于实现）
        // 但对于同一个代理，多次调用应该一致
        assertEquals(proxy1.hashCode(), proxy1.hashCode());
    }

    @Test
    void testProxyEquals() {
        OrderService proxy = ExtProxyFactory.createProxy(String.valueOf(OrderService.class));

        // 代理等于自身
        assertEquals(proxy, proxy);

        // 代理不等于其他对象
        assertNotEquals(proxy, new Object());
    }

    @Test
    void testProxyMultipleMethods() {
        OrderService proxy = ExtProxyFactory.createProxy(String.valueOf(OrderService.class));

        // 测试多个方法
        String createResult = proxy.createOrder("P001", 10);
        assertTrue(createResult.contains("PLATFORM"));

        boolean cancelResult = proxy.cancelOrder("O123");
        assertTrue(cancelResult);

        String getResult = proxy.getOrder("O123");
        assertTrue(getResult.contains("O123"));
    }
}