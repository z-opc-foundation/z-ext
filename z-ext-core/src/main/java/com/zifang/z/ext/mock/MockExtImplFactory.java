package com.zifang.z.ext.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Mock 实现的工厂 —— 「零代码实现」的落地。
 * <p>
 * 控制面下发一段 {@link MockSpec}，这里凭空造出一个实现该扩展点接口的动态代理，
 * 注册进注册表，业务侧<b>一行 Java 都不用写</b>就能被接管。
 * <p>
 * 返回值适配：配置的是字符串而方法要的是对象时，用 Jackson 按目标类型反序列化；
 * 目标是基本类型而配置为 null 时，返回该类型的默认值（不抛 NPE）。
 */
public final class MockExtImplFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockExtImplFactory() {
    }

    /**
     * 造一个 mock 实现并注册到扩展点。
     *
     * @param point         扩展点标识
     * @param interfaceClass 扩展点接口
     * @param spec          行为描述
     * @return 注册的实现名
     */
    public static String register(String point, Class<?> interfaceClass, MockSpec spec) {
        if (point == null || point.trim().isEmpty()) {
            throw new IllegalArgumentException("扩展点标识不能为空");
        }
        if (interfaceClass == null || !interfaceClass.isInterface()) {
            throw new IllegalArgumentException("扩展点接口不存在或不是接口: " + interfaceClass);
        }
        if (spec == null || spec.getName() == null || spec.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("mock 实现名不能为空");
        }
        ExtPointDefinition pointDef = ExtRegistry.getPoint(point);
        if (pointDef == null) {
            throw new IllegalArgumentException("扩展点不存在: " + point);
        }
        String name = spec.getName().trim();
        for (ExtImplDefinition existing : ExtRegistry.getImplementations(point)) {
            if (name.equals(existing.getName())) {
                if (existing.getImplClass() != null && existing.getImplClass().equals(interfaceClass)) {
                    // 同名同接口 ⇒ 视为重注册：先摘掉旧的再挂新的
                    ExtRegistry.unregisterImpl(point, name);
                    break;
                }
                throw new IllegalArgumentException("扩展实现名冲突: point=" + point + ", name=" + name);
            }
        }
        Object proxy = Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new MockInvocationHandler(spec));

        ExtImplDefinition definition = new ExtImplDefinition(point, name, interfaceClass);
        definition.setInstance(proxy);
        definition.setType(ExtImplType.CUSTOM);
        definition.setDescription(spec.getDescription() == null ? "控制面注入的 mock 实现" : spec.getDescription());
        definition.setEnabled(true);
        definition.setOrder(100);
        ExtRegistry.registerImpl(definition);
        return name;
    }

    /**
     * mock 代理的调用处理：按行为描述决定返回值/异常/拒绝。
     */
    private static class MockInvocationHandler implements InvocationHandler {

        private final MockSpec spec;

        MockInvocationHandler(MockSpec spec) {
            this.spec = spec;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            if (spec.getDelayMs() > 0) {
                try {
                    Thread.sleep(spec.getDelayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
            switch (spec.getBehavior()) {
                case THROW:
                    throw new IllegalStateException(spec.getErrorMessage() == null
                            ? "mock 实现按要求抛错" : spec.getErrorMessage());
                case DENY:
                    return denyValue(method);
                case RETURN:
                default:
                    return coerce(spec.getResult(), method.getReturnType());
            }
        }

        /**
         * 恒拒绝时的返回值：布尔给 false，字符串给拒绝理由，其余给 null。
         */
        private Object denyValue(Method method) {
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class || returnType == Boolean.class) {
                return Boolean.FALSE;
            }
            if (returnType == String.class) {
                return spec.getErrorMessage();
            }
            return defaultValue(returnType);
        }
    }

    /**
     * 把配置值适配成方法签名要的类型。
     */
    static Object coerce(Object value, Class<?> target) {
        if (target == void.class || target == Void.class) {
            return null;
        }
        if (value == null) {
            return defaultValue(target);
        }
        if (target.isInstance(value)) {
            return value;
        }
        // 基本类型目标 + 装箱值（Boolean.TRUE → boolean）⇒ 直接返回，反射调用会自动拆箱
        Class<?> boxed = boxedOf(target);
        if (boxed != null && boxed.isInstance(value)) {
            return value;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            try {
                return MAPPER.readValue(text, target);
            } catch (Exception e) {
                // 目标类型不是 JSON 能解出来的 ⇒ 退回字符串形态（方法本身接受 String 时已在上面命中）
                return defaultValue(target);
            }
        }
        return defaultValue(target);
    }

    /**
     * 基本类型对应的装箱类型。
     */
    private static Class<?> boxedOf(Class<?> target) {
        if (target == boolean.class) {
            return Boolean.class;
        }
        if (target == char.class) {
            return Character.class;
        }
        if (target == byte.class) {
            return Byte.class;
        }
        if (target == short.class) {
            return Short.class;
        }
        if (target == int.class) {
            return Integer.class;
        }
        if (target == long.class) {
            return Long.class;
        }
        if (target == float.class) {
            return Float.class;
        }
        if (target == double.class) {
            return Double.class;
        }
        return null;
    }

    /**
     * 基本类型的默认值，避免自动拆箱 NPE。
     */
    static Object defaultValue(Class<?> target) {
        if (!target.isPrimitive()) {
            return null;
        }
        if (target == boolean.class) {
            return Boolean.FALSE;
        }
        if (target == char.class) {
            return (char) 0;
        }
        if (target == byte.class) {
            return (byte) 0;
        }
        if (target == short.class) {
            return (short) 0;
        }
        if (target == int.class) {
            return 0;
        }
        if (target == long.class) {
            return 0L;
        }
        if (target == float.class) {
            return 0F;
        }
        if (target == double.class) {
            return 0D;
        }
        return null;
    }
}
