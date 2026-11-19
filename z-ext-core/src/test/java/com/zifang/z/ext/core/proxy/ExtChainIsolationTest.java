package com.zifang.z.ext.core.proxy;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.test.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CHAIN 执行语义回归：失败隔离。
 * <p>
 * CHAIN 的典型场景是「同一事实的多个独立消费者」（支付完成 → 应收登记 + 许可签发）。
 * 单个实现抛异常只记日志，<b>后续实现必须继续执行</b>，异常不上抛调用方。
 */
class ExtChainIsolationTest {

    /** 记录每个实现的执行痕迹（含失败实现是否被调用过）。 */
    private static final List<String> TRACES = new ArrayList<>();

    private static final OrderService BOOM_IMPL = new OrderService() {
        @Override
        public String createOrder(String productId, int quantity) {
            TRACES.add("boom");
            throw new IllegalStateException("第一个消费者挂了");
        }

        @Override
        public boolean cancelOrder(String orderId) {
            return true;
        }

        @Override
        public String getOrder(String orderId) {
            return "boom";
        }
    };

    private static final OrderService SURVIVOR_IMPL = new OrderService() {
        @Override
        public String createOrder(String productId, int quantity) {
            TRACES.add("survivor:" + productId);
            return "survivor-ok";
        }

        @Override
        public boolean cancelOrder(String orderId) {
            return true;
        }

        @Override
        public String getOrder(String orderId) {
            return "survivor";
        }
    };

    @BeforeEach
    void setUp() {
        ExtRegistry.clear();
        TRACES.clear();

        ExtPointDefinition pointDef = new ExtPointDefinition();
        pointDef.setPoint("chain.isolation");
        pointDef.setInterfaceClass(OrderService.class);
        pointDef.setType(ExtType.CHAIN);
        ExtRegistry.registerPoint(pointDef);

        ExtImplDefinition first = new ExtImplDefinition();
        first.setPoint("chain.isolation");
        first.setName("boom");
        first.setImplClass(BOOM_IMPL.getClass());
        first.setType(ExtImplType.PLATFORM);
        first.setEnabled(true);
        first.setOrder(1);
        first.setInstance(BOOM_IMPL);
        ExtRegistry.registerImpl(first);

        ExtImplDefinition second = new ExtImplDefinition();
        second.setPoint("chain.isolation");
        second.setName("survivor");
        second.setImplClass(SURVIVOR_IMPL.getClass());
        second.setType(ExtImplType.CUSTOM);
        second.setEnabled(true);
        second.setOrder(2);
        second.setInstance(SURVIVOR_IMPL);
        ExtRegistry.registerImpl(second);
    }

    @AfterEach
    void tearDown() {
        ExtRegistry.clear();
    }

    @Test
    void chainContinuesAfterSingleImplFailure() {
        OrderService proxy = ExtProxyFactory.createProxy("chain.isolation");

        String result = proxy.createOrder("P001", 1);

        // 失败不上抛：调用方拿到的是最后一个成功实现的结果
        assertEquals("survivor-ok", result);
        // 失败实现确实被调过，但后续实现没有被它吞掉
        assertEquals(List.of("boom", "survivor:P001"), TRACES);
    }

    @Test
    void chainWithoutFailureRunsAllImpls() {
        ExtRegistry.getImplementations("chain.isolation").get(0).setEnabled(false);
        TRACES.clear();

        OrderService proxy = ExtProxyFactory.createProxy("chain.isolation");
        String result = proxy.createOrder("P002", 2);

        assertEquals("survivor-ok", result);
        assertEquals(List.of("survivor:P002"), TRACES);
        assertTrue(ExtRegistry.getImplementations("chain.isolation").size() == 2);
    }
}
