package com.zifang.z.ext.spring;

import com.zifang.z.ext.core.proxy.ExtInvocationHandler;
import com.zifang.z.ext.core.proxy.ExtProxyFactory;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.router.ExtRouter;

import java.util.concurrent.Executor;

/**
 * 容器化的代理构建器。
 * <p>
 * 把 Spring 容器里装配好的路由策略与异步执行器注入每次代理创建，
 * 业务侧注入本件即可拿到与容器配置一致的路由行为，而不必手工拼 handler。
 */
public class ExtProxyBuilder {

    private final ExtRouter router;
    private final Executor asyncExecutor;

    public ExtProxyBuilder(ExtRouter router, Executor asyncExecutor) {
        this.router = router;
        this.asyncExecutor = asyncExecutor;
    }

    /** 当前装配的异步执行器；null = 未装配（ASYNC 点退化为同步）。 */
    public Executor getAsyncExecutor() {
        return asyncExecutor;
    }

    /**
     * 按扩展点定义创建代理。
     */
    public <T> T create(ExtPointDefinition pointDef) {
        return ExtProxyFactory.createProxy(pointDef, this::customize);
    }

    /**
     * 按扩展点标识（或接口类型描述符）创建代理。
     */
    public <T> T create(String point) {
        return ExtProxyFactory.createProxy(point, this::customize);
    }

    private void customize(ExtInvocationHandler handler) {
        if (router != null) {
            handler.setRouter(router);
        }
        if (asyncExecutor != null) {
            handler.setAsyncExecutor(asyncExecutor);
        }
    }
}
