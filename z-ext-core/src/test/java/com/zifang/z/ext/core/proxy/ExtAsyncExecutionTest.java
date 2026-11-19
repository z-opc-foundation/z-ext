package com.zifang.z.ext.core.proxy;

import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异步执行语义测试：ASYNC 类型在配置执行器后 fire-and-forget，未配置时保持同步。
 */
class ExtAsyncExecutionTest {

    /**
     * 测试用扩展点接口。
     */
    public interface GreetingService {
        String greet(String name);
    }

    /**
     * 测试用实现。
     */
    public static class GreetingServiceImpl implements GreetingService {
        @Override
        public String greet(String name) {
            return "hello " + name;
        }
    }

    private ExtPointDefinition pointDef;

    @BeforeEach
    void setUp() {
        ExtRegistry.clear();
        pointDef = new ExtPointDefinition();
        pointDef.setPoint("greeting.service");
        pointDef.setInterfaceClass(GreetingService.class);
        pointDef.setType(ExtType.ASYNC);
        ExtRegistry.registerPoint(pointDef);

        ExtImplDefinition impl = new ExtImplDefinition();
        impl.setPoint("greeting.service");
        impl.setName("default");
        impl.setType(com.zifang.z.ext.annotation.ExtImplType.PLATFORM);
        impl.setImplClass(GreetingServiceImpl.class);
        impl.setEnabled(true);
        ExtRegistry.registerImpl(impl);
    }

    @AfterEach
    void tearDown() {
        ExtRegistry.clear();
    }

    @Test
    void withoutExecutorKeepsSynchronousBehaviour() {
        GreetingService proxy = ExtProxyFactory.createProxy(pointDef);
        assertEquals("hello world", proxy.greet("world"));
    }

    @Test
    void withExecutorRunsAsynchronouslyAndReturnsNull() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> invoked = new AtomicReference<>();

        ExtInvocationHandler handler = new ExtInvocationHandler(pointDef);
        handler.setAsyncExecutor(task -> executor.execute(() -> {
            task.run();
            latch.countDown();
        }));

        GreetingService proxy = ExtProxyFactory.createProxy(GreetingService.class, handler);
        Object result = proxy.greet("world");

        assertNull(result, "异步执行不返回结果");
        assertTrue(latch.await(5, TimeUnit.SECONDS), "异步任务应被执行");
        executor.shutdown();
    }

    @Test
    void asyncFailureDoesNotPropagateToCaller() {
        ExtImplDefinition broken = new ExtImplDefinition();
        broken.setPoint("greeting.service");
        broken.setName("broken");
        broken.setImplClass(BrokenGreetingService.class);
        broken.setEnabled(true);
        broken.setOrder(0);
        ExtRegistry.registerImpl(broken);
        ExtRegistry.setActiveImpl("greeting.service", "broken");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExtInvocationHandler handler = new ExtInvocationHandler(pointDef);
        handler.setAsyncExecutor(executor);

        GreetingService proxy = ExtProxyFactory.createProxy(GreetingService.class, handler);
        assertNull(proxy.greet("world"), "异步执行失败不应抛回调用方");

        executor.shutdown();
    }

    /**
     * 会抛异常的实现。
     */
    public static class BrokenGreetingService implements GreetingService {
        @Override
        public String greet(String name) {
            throw new IllegalStateException("boom");
        }
    }
}
