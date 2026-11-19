package com.zifang.z.ext.spring.preset;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * servlet 对象的反射访问器 —— 让预置闸门**不依赖 javax/jakarta 命名空间**。
 * <p>
 * Spring 5 的 web 层用的是 {@code javax.servlet}，Spring 6 换成了 {@code jakarta.servlet}。
 * 预置闸门如果直接按其中一种命名空间编译，另一类宿主就会在类加载时炸掉
 * （{@code NoClassDefFoundError}），而这类宿主恰恰是最不该被一个「埋点」拖挂的。
 * <p>
 * 这里改为反射取原生对象上的方法：命名空间是哪个就用哪个，两边都能跑。
 */
final class ServletNativeAccess {

    private ServletNativeAccess() {
    }

    /**
     * 取请求方法（{@code GET}/{@code POST}/…）。
     *
     * @param nativeRequest 原生请求对象（HttpServletRequest）
     * @return 请求方法；取不到返回 {@code UNKNOWN}
     */
    static String requestMethod(Object nativeRequest) {
        Object value = invoke(nativeRequest, "getMethod");
        return value instanceof String ? (String) value : "UNKNOWN";
    }

    /**
     * 取请求路径。
     */
    static String requestUri(Object nativeRequest) {
        Object value = invoke(nativeRequest, "getRequestURI");
        return value instanceof String ? (String) value : "";
    }

    /**
     * 取客户端地址。
     */
    static String remoteAddress(Object nativeRequest) {
        Object value = invoke(nativeRequest, "getRemoteAddr");
        return value instanceof String ? (String) value : null;
    }

    /**
     * 取请求参数表（取不到时给空表，避免上层判空）。
     */
    @SuppressWarnings("unchecked")
    static Map<String, String[]> parameterMap(Object nativeRequest) {
        Object value = invoke(nativeRequest, "getParameterMap");
        if (value instanceof Map) {
            return (Map<String, String[]>) value;
        }
        return new HashMap<>();
    }

    /**
     * 读请求属性。
     */
    static Object getAttribute(Object nativeRequest, String name) {
        return invoke(nativeRequest, "getAttribute", new Class<?>[]{String.class}, new Object[]{name});
    }

    /**
     * 写请求属性（异常吞掉：属性写不进去不该让请求失败）。
     */
    static void setAttribute(Object nativeRequest, String name, Object value) {
        invoke(nativeRequest, "setAttribute",
                new Class<?>[]{String.class, Object.class}, new Object[]{name, value});
    }

    /**
     * 写拒绝响应。
     *
     * @param nativeResponse 原生响应对象（HttpServletResponse）
     * @param status         状态码
     * @param contentType    内容类型
     * @param body           响应体
     */
    static void write(Object nativeResponse, int status, String contentType, String body) {
        if (nativeResponse == null) {
            return;
        }
        invoke(nativeResponse, "setStatus", new Class<?>[]{int.class}, new Object[]{status});
        invoke(nativeResponse, "setContentType", new Class<?>[]{String.class}, new Object[]{contentType});
        Object writer = invoke(nativeResponse, "getWriter");
        if (writer instanceof PrintWriter) {
            PrintWriter printWriter = (PrintWriter) writer;
            printWriter.write(body);
            printWriter.flush();
        }
    }

    private static Object invoke(Object target, String methodName) {
        return invoke(target, methodName, new Class<?>[0], new Object[0]);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] paramTypes, Object[] args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }
}
