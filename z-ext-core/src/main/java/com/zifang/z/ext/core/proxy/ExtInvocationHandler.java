package com.zifang.z.ext.core.proxy;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.context.ExtContextHolder;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.core.router.ExtRouter;
import com.zifang.z.ext.core.router.ExtRouterContext;
import com.zifang.z.ext.rpc.client.ExtRpcInvoker;
import com.zifang.z.ext.rpc.client.ExtRpcInvokerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 扩展调用处理器
 * 代理的核心，负责路由选择和执行
 */
public class ExtInvocationHandler implements InvocationHandler {

    private static final Logger log = LogManager.getLogger(ExtInvocationHandler.class);

    /**
     * 扩展点定义
     */
    private final ExtPointDefinition pointDef;
    /**
     * 缓存路由结果，避免每次都路由
     */
    private final Map<String, List<ExtImplDefinition>> routeCache = new ConcurrentHashMap<>();
    /**
     * 路由策略
     */
    private ExtRouter router;
    /**
     * 异步执行器。
     * <p>
     * {@link ExtType#ASYNC} 的扩展点由它决定是否真的异步：未设置时退化为同步执行
     * （与框架早期行为一致，避免存量扩展点被静默改成 fire-and-forget）。
     */
    private volatile Executor asyncExecutor;

    public ExtInvocationHandler(ExtPointDefinition pointDef) {
        this.pointDef = pointDef;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 排除 Object 方法
        if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(proxy, method, args);
        }

        // 构建路由上下文
        ExtRouterContext context = new ExtRouterContext(method.getName(), args);
        // 线程上下文里的租户/灰度标签等属性并入路由上下文，供条件表达式引用
        context.getAttributes().putAll(ExtContextHolder.getAll());

        // 获取路由结果
        List<ExtImplDefinition> impls = getRoutedImplementations(context);

        if (impls == null || impls.isEmpty()) {
            throw new IllegalStateException("No implementation found for extension point: " + pointDef.getPoint());
        }

        // 根据扩展点类型执行
        ExtType extType = pointDef.getType();
        if (extType == ExtType.CHAIN) {
            return invokeChain(method, args, impls);
        } else if (extType == ExtType.ASYNC && asyncExecutor != null) {
            return invokeAsync(method, args, impls);
        } else {
            return invokeOne(method, args, impls.get(0));
        }
    }

    /**
     * 获取路由后的实现列表
     */
    private List<ExtImplDefinition> getRoutedImplementations(ExtRouterContext context) {
        String cacheKey = context.getMethodName();
        // 简单缓存，实际应该更复杂
        // 可以根据参数生成缓存key

        // 获取所有启用的实现
        List<ExtImplDefinition> impls = ExtRegistry.getEnabledImplementations(pointDef.getPoint());
        if (impls.isEmpty()) {
            return new ArrayList<>();
        }

        // 使用路由选择
        ExtRouter router = getRouter();
        return router.route(pointDef, impls, context);
    }

    /**
     * 获取路由策略
     */
    private ExtRouter getRouter() {
        if (router == null) {
            router = new com.zifang.z.ext.core.router.DefaultExtRouter();
        }
        return router;
    }

    /**
     * 设置自定义路由策略
     */
    public void setRouter(ExtRouter router) {
        this.router = router;
        clearCache();
    }

    /**
     * 执行单个实现（SYNC/ASYNC类型）
     */
    private Object invokeOne(Method method, Object[] args, ExtImplDefinition impl) throws Throwable {
        // 根据实现类型选择不同的调用方式
        ExtImplType implType = impl.getType();

        switch (implType) {
            case PLATFORM:
            case CUSTOM:
                // 本地调用
                return invokeLocal(method, args, impl);

            case EXTERNAL:
                // RPC调用
                return invokeRemote(method, args, impl);

            default:
                throw new IllegalArgumentException("Unknown implementation type: " + implType);
        }
    }

    /**
     * 链式执行（CHAIN类型）
     * <p>
     * 失败隔离：单个实现抛异常只记日志、继续跑后续实现。CHAIN 的典型场景是
     * 「同一事实的多个独立消费者」（如支付完成 → 应收登记 + 许可签发），
     * 一个消费者挂掉不能吞掉其余消费者的副作用，也不能把异常上抛炸掉调用方主流程。
     * 返回值口径：最后一个<b>成功</b>实现的结果（与逐个覆盖写的历史行为一致）。
     */
    private Object invokeChain(Method method, Object[] args, List<ExtImplDefinition> impls) throws Throwable {
        Object result = null;
        for (ExtImplDefinition impl : impls) {
            try {
                result = invokeOne(method, args, impl);
            } catch (Throwable e) {
                log.error("[z-ext] 链式扩展点单个实现失败（继续执行后续实现）: point={}, impl={}",
                        pointDef.getPoint(), impl.getName(), e);
            }
        }
        return result;
    }

    /**
     * 异步执行（ASYNC类型且配置了异步执行器）
     * <p>
     * fire-and-forget：立即返回 {@code null}，异常只记日志不抛回调用方。
     */
    private Object invokeAsync(Method method, Object[] args, List<ExtImplDefinition> impls) {
        try {
            asyncExecutor.execute(() -> {
                try {
                    for (ExtImplDefinition impl : impls) {
                        invokeOne(method, args, impl);
                    }
                } catch (Throwable e) {
                    log.error("[z-ext] 异步扩展点执行失败: point={}, method={}",
                            pointDef.getPoint(), method.getName(), e);
                }
            });
        } catch (RuntimeException e) {
            log.error("[z-ext] 异步扩展点提交失败: point={}, method={}",
                    pointDef.getPoint(), method.getName(), e);
        }
        return null;
    }

    /**
     * 设置异步执行器；设置后 {@link ExtType#ASYNC} 的扩展点不再阻塞调用方。
     */
    public void setAsyncExecutor(Executor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    public Executor getAsyncExecutor() {
        return asyncExecutor;
    }

    /**
     * 本地调用
     */
    private Object invokeLocal(Method method, Object[] args, ExtImplDefinition impl) throws Exception {
        Object instance = impl.getInstance();
        if (instance == null) {
            // 延迟创建实例
            try {
                instance = impl.getImplClass().getDeclaredConstructor().newInstance();
                impl.setInstance(instance);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("No default constructor for: " + impl.getImplClass(), e);
            }
        }

        method.setAccessible(true);
        return method.invoke(instance, args);
    }

    /**
     * 远程RPC调用
     */
    private Object invokeRemote(Method method, Object[] args, ExtImplDefinition impl) throws Throwable {
        // 使用RPC调用器
        ExtRpcInvoker invoker = ExtRpcInvokerFactory.getInvoker(
                impl.getRpcAddress(),
                impl.getRpcPort()
        );

        return invoker.invoke(
                pointDef.getInterfaceClass(),
                method.getName(),
                method.getParameterTypes(),
                args
        );
    }

    /**
     * 处理 Object 方法
     */
    private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        String methodName = method.getName();

        if ("toString".equals(methodName)) {
            return "ExtProxy[" + pointDef.getPoint() + "]";
        } else if ("hashCode".equals(methodName)) {
            return pointDef.getPoint().hashCode();
        } else if ("equals".equals(methodName)) {
            return proxy == args[0];
        } else {
            return null;
        }
    }

    /**
     * 清理缓存
     */
    public void clearCache() {
        routeCache.clear();
    }
}