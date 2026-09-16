package com.zifang.z.ext.core.script;

import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 脚本驱动的扩展实现桥接。
 * 将 z-script 中注册的脚本包装为 ExtImpl，通过动态代理执行。
 */
public class ScriptBackedExtImpl {

    private final ScriptExecutor executor;

    public ScriptBackedExtImpl(ScriptExecutor executor) {
        this.executor = executor;
    }

    @SuppressWarnings("unchecked")
    public <T> T createProxy(String pointName, String scriptCode, String dslType, Class<T> interfaceClass) {
        ExtImplDefinition def = new ExtImplDefinition();
        def.setPoint(pointName);
        def.setName("script-" + scriptCode.hashCode());
        def.setImplClass(interfaceClass);
        def.setType(com.zifang.z.ext.annotation.ExtImplType.CUSTOM);
        def.setDescription("Script-backed implementation");
        def.setEnabled(true);
        def.setOrder(100);

        Object proxy = Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class[]{interfaceClass},
                new ScriptInvocationHandler(pointName, scriptCode, dslType, executor)
        );

        ExtRegistry.registerImpl(def);
        return (T) proxy;
    }

    public interface ScriptExecutor {
        Object execute(String scriptCode, String dslType, String methodName, Object[] args);
    }

    private static class ScriptInvocationHandler implements InvocationHandler {
        private final String pointName;
        private final String scriptCode;
        private final String dslType;
        private final ScriptExecutor executor;

        ScriptInvocationHandler(String pointName, String scriptCode, String dslType, ScriptExecutor executor) {
            this.pointName = pointName;
            this.scriptCode = scriptCode;
            this.dslType = dslType;
            this.executor = executor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            return executor.execute(scriptCode, dslType, method.getName(), args);
        }
    }
}
