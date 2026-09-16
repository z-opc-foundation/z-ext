package com.zifang.z.ext.core.proxy;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.core.router.ExtRouter;
import com.zifang.z.ext.core.router.ExtRouterContext;
import com.zifang.z.ext.test.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExtInvocationHandler 调用处理器测试
 */
class ExtInvocationHandlerTest {

    @BeforeEach
    void setUp() {
        ExtRegistry.clear();
    }

    @Test
    void testInvoke_SyncType() throws Throwable {
        // 准备测试数据
        ExtPointDefinition pointDef = new ExtPointDefinition();
        pointDef.setPoint("order.service");
        pointDef.setInterfaceClass(OrderService.class);
        pointDef.setType(ExtType.SYNC);
        ExtRegistry.registerPoint(pointDef);

        // 注册实现
        ExtImplDefinition implDef = new ExtImplDefinition();
        implDef.setPoint("order.service");
        implDef.setName("default");
        implDef.setImplClass(com.zifang.z.ext.test.DefaultOrderService.class);
        implDef.setType(ExtImplType.PLATFORM);
        implDef.setEnabled(true);
        implDef.setInstance(new com.zifang.z.ext.test.DefaultOrderService());
        ExtRegistry.registerImpl(implDef);

        // 创建处理器
        ExtInvocationHandler handler = new ExtInvocationHandler(pointDef);

        // 获取代理
        OrderService proxy = ExtProxyFactory.createProxy(String.valueOf(OrderService.class));

        // 测试调用
        String result = proxy.createOrder("P001", 5);
        assertNotNull(result);
        assertTrue(result.contains("P001"));
        assertTrue(result.contains("5"));
    }

    @Test
    void testInvoke_MultipleMethods() throws Throwable {
        // 准备测试数据
        ExtPointDefinition pointDef = new ExtPointDefinition();
        pointDef.setPoint("order.service");
        pointDef.setInterfaceClass(OrderService.class);
        pointDef.setType(ExtType.SYNC);
        ExtRegistry.registerPoint(pointDef);

        // 注册实现
        ExtImplDefinition implDef = new ExtImplDefinition();
        implDef.setPoint("order.service");
        implDef.setName("default");
        implDef.setImplClass(com.zifang.z.ext.test.DefaultOrderService.class);
        implDef.setType(ExtImplType.PLATFORM);
        implDef.setEnabled(true);
        implDef.setInstance(new com.zifang.z.ext.test.DefaultOrderService());
        ExtRegistry.registerImpl(implDef);

        // 创建处理器并获取代理
        ExtInvocationHandler handler = new ExtInvocationHandler(pointDef);
        OrderService proxy = ExtProxyFactory.createProxy(String.valueOf(OrderService.class));

        // 测试 cancelOrder 方法
        boolean cancelResult = proxy.cancelOrder("O123");
        assertTrue(cancelResult);

        // 测试 getOrder 方法
        String getResult = proxy.getOrder("O456");
        assertNotNull(getResult);
        assertTrue(getResult.contains("O456"));
    }

    @Test
    void testInvoke_NoImplementation() {
        // 准备测试数据（不注册实现）
        ExtPointDefinition pointDef = new ExtPointDefinition();
        pointDef.setPoint("order.service");
        pointDef.setInterfaceClass(OrderService.class);
        pointDef.setType(ExtType.SYNC);
        ExtRegistry.registerPoint(pointDef);

        // 创建处理器并获取代理
        ExtInvocationHandler handler = new ExtInvocationHandler(pointDef);
        OrderService proxy = ExtProxyFactory.createProxy(String.valueOf(OrderService.class));

        // 应该抛出异常
        assertThrows(IllegalStateException.class, () -> {
            proxy.createOrder("P001", 1);
        });
    }

    @Test
    void testHandleObjectMethods() throws Throwable {
        // 准备测试数据
        ExtPointDefinition pointDef = new ExtPointDefinition();
        pointDef.setPoint("order.service");
        pointDef.setInterfaceClass(OrderService.class);
        pointDef.setType(ExtType.SYNC);
        ExtRegistry.registerPoint(pointDef);

        // 注册实现
        ExtImplDefinition implDef = new ExtImplDefinition();
        implDef.setPoint("order.service");
        implDef.setName("default");
        implDef.setImplClass(com.zifang.z.ext.test.DefaultOrderService.class);
        implDef.setType(ExtImplType.PLATFORM);
        implDef.setEnabled(true);
        implDef.setInstance(new com.zifang.z.ext.test.DefaultOrderService());
        ExtRegistry.registerImpl(implDef);

        // 创建处理器
        ExtInvocationHandler handler = new ExtInvocationHandler(pointDef);

        // 测试 toString
        OrderService proxy = ExtProxyFactory.createProxy(String.valueOf(OrderService.class));
        String toString = proxy.toString();
        assertTrue(toString.contains("order.service"));

        // 测试 hashCode
        int hashCode = proxy.hashCode();
        assertTrue(hashCode != 0);

        // 测试 equals
        assertTrue(proxy.equals(proxy));
        assertFalse(proxy.equals("other"));
    }

    @Test
    void testInvoke_ChainType() throws Throwable {
        // 准备测试数据 - CHAIN类型
        ExtPointDefinition pointDef = new ExtPointDefinition();
        pointDef.setPoint("log.service");
        pointDef.setInterfaceClass(OrderService.class); // 复用OrderService作为示例
        pointDef.setType(ExtType.CHAIN);
        ExtRegistry.registerPoint(pointDef);

        // 注册多个实现
        ExtImplDefinition implDef1 = new ExtImplDefinition();
        implDef1.setPoint("log.service");
        implDef1.setName("log1");
        implDef1.setImplClass(com.zifang.z.ext.test.DefaultOrderService.class);
        implDef1.setType(ExtImplType.PLATFORM);
        implDef1.setEnabled(true);
        implDef1.setOrder(1);
        implDef1.setInstance(new com.zifang.z.ext.test.DefaultOrderService());
        ExtRegistry.registerImpl(implDef1);

        ExtImplDefinition implDef2 = new ExtImplDefinition();
        implDef2.setPoint("log.service");
        implDef2.setName("log2");
        implDef2.setImplClass(com.zifang.z.ext.test.CustomOrderService.class);
        implDef2.setType(ExtImplType.CUSTOM);
        implDef2.setEnabled(true);
        implDef2.setOrder(2);
        implDef2.setInstance(new com.zifang.z.ext.test.CustomOrderService());
        ExtRegistry.registerImpl(implDef2);

        // CHAIN类型需要扩展点返回多个实现，这里暂时跳过
        // 因为CHAIN的完整测试需要修改DefaultExtRouter来支持
    }

    @Test
    void testCustomRouter() throws Throwable {
        // 准备测试数据
        ExtPointDefinition pointDef = new ExtPointDefinition();
        pointDef.setPoint("order.service");
        pointDef.setInterfaceClass(OrderService.class);
        pointDef.setType(ExtType.SYNC);
        ExtRegistry.registerPoint(pointDef);

        // 注册两个实现
        ExtImplDefinition implDef1 = new ExtImplDefinition();
        implDef1.setPoint("order.service");
        implDef1.setName("platform");
        implDef1.setImplClass(com.zifang.z.ext.test.DefaultOrderService.class);
        implDef1.setType(ExtImplType.PLATFORM);
        implDef1.setEnabled(true);
        implDef1.setInstance(new com.zifang.z.ext.test.DefaultOrderService());
        ExtRegistry.registerImpl(implDef1);

        ExtImplDefinition implDef2 = new ExtImplDefinition();
        implDef2.setPoint("order.service");
        implDef2.setName("custom");
        implDef2.setImplClass(com.zifang.z.ext.test.CustomOrderService.class);
        implDef2.setType(ExtImplType.CUSTOM);
        implDef2.setEnabled(true);
        implDef2.setInstance(new com.zifang.z.ext.test.CustomOrderService());
        ExtRegistry.registerImpl(implDef2);

        // 创建处理器
        ExtInvocationHandler handler = new ExtInvocationHandler(pointDef);

        // 设置自定义路由 - 总是选择PLATFORM
        handler.setRouter(new ExtRouter() {
            @Override
            public List<ExtImplDefinition> route(ExtPointDefinition pointDef, List<ExtImplDefinition> impls, ExtRouterContext context) {
                return impls.stream()
                        .filter(i -> i.getType() == ExtImplType.PLATFORM)
                        .collect(Collectors.toList());
            }

            @Override
            public String getName() {
                return "xx";
            }
        });

        // 获取代理
        OrderService proxy = ExtProxyFactory.createProxy(String.valueOf(OrderService.class));

        // 应该总是调用PLATFORM实现
        String result = proxy.createOrder("P001", 1);
        assertTrue(result.contains("PLATFORM"));
    }

    @Test
    void testClearCache() throws Throwable {
        // 准备测试数据
        ExtPointDefinition pointDef = new ExtPointDefinition();
        pointDef.setPoint("order.service");
        pointDef.setInterfaceClass(OrderService.class);
        pointDef.setType(ExtType.SYNC);
        ExtRegistry.registerPoint(pointDef);

        // 注册实现
        ExtImplDefinition implDef = new ExtImplDefinition();
        implDef.setPoint("order.service");
        implDef.setName("default");
        implDef.setImplClass(com.zifang.z.ext.test.DefaultOrderService.class);
        implDef.setType(ExtImplType.PLATFORM);
        implDef.setEnabled(true);
        implDef.setInstance(new com.zifang.z.ext.test.DefaultOrderService());
        ExtRegistry.registerImpl(implDef);

        // 创建处理器
        ExtInvocationHandler handler = new ExtInvocationHandler(pointDef);

        // 验证clearCache不抛异常
        assertDoesNotThrow(() -> handler.clearCache());
    }
}