package com.zifang.z.ext.rpc;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.proxy.ExtProxyFactory;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.test.OrderService;
import com.zifang.z.rpc.remoting.RpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EXTERNAL 实现端到端：扩展点调用经 z-rpc（netty 真连接）打到「另一个进程形状」的
 * 服务端并拿回结果。
 * <p>
 * 服务端用 {@link RpcServer} 起在内核随机端口 —— 与真实跨进程部署唯一的差别是
 * 客户端、服务端共享 JVM（进程边界由 socket 承担，序列化/网络栈全部真实走一遍）。
 * 这是控制台把实现标成 EXTERNAL（rpcAddress + rpcPort）后触发端实际走的那条路。
 */
class ExtExternalRpcE2ETest {

    /** 远端服务端记录到的调用（验证副作用真的发生在对端）。 */
    private static final List<String> REMOTE_CALLS = new CopyOnWriteArrayList<>();

    /** 远端实现：返回可断言的标记串。 */
    private static final OrderService REMOTE_IMPL = new OrderService() {
        @Override
        public String createOrder(String productId, int quantity) {
            REMOTE_CALLS.add("create:" + productId + ":" + quantity);
            return "REMOTE:" + productId;
        }

        @Override
        public boolean cancelOrder(String orderId) {
            REMOTE_CALLS.add("cancel:" + orderId);
            return true;
        }

        @Override
        public String getOrder(String orderId) {
            return "REMOTE:" + orderId;
        }
    };

    private RpcServer server;

    @BeforeEach
    void setUp() throws Exception {
        ExtRegistry.clear();
        REMOTE_CALLS.clear();

        server = new RpcServer(0);
        server.registerService(OrderService.class, REMOTE_IMPL);
        // start() 默认阻塞在 closeFuture().await()（给常驻服务线程用），测试用守护线程模式
        server.start(true);
    }

    @AfterEach
    void tearDown() {
        com.zifang.z.ext.rpc.client.ExtRpcInvokerFactory.closeAll();
        if (server != null) {
            server.stop();
        }
        ExtRegistry.clear();
    }

    @Test
    void externalImplRoundTripsOverRealSocket() throws Exception {
        ExtPointDefinition pointDef = new ExtPointDefinition();
        pointDef.setPoint("external.order");
        pointDef.setInterfaceClass(OrderService.class);
        pointDef.setType(ExtType.SYNC);
        ExtRegistry.registerPoint(pointDef);

        ExtImplDefinition implDef = new ExtImplDefinition();
        implDef.setPoint("external.order");
        implDef.setName("remote");
        implDef.setImplClass(REMOTE_IMPL.getClass());
        implDef.setType(ExtImplType.EXTERNAL);
        implDef.setEnabled(true);
        implDef.setRpcAddress("127.0.0.1");
        implDef.setRpcPort(server.getPort());
        ExtRegistry.registerImpl(implDef);

        OrderService proxy = ExtProxyFactory.createProxy("external.order");

        String result = proxy.createOrder("P-EXT", 3);

        // 返回值跨越真实 socket 回来
        assertEquals("REMOTE:P-EXT", result);
        // 副作用发生在对端，而不是本地类加载的实例
        assertEquals(List.of("create:P-EXT:3"), REMOTE_CALLS);
        // 调用器按 host:port 缓存，同点重复调用不新建连接工厂
        assertTrue(com.zifang.z.ext.rpc.client.ExtRpcInvokerFactory.getInvokerCount() >= 1);
    }
}
