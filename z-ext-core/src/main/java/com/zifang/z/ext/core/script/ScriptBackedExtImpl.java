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
        def.setName("script-" + nameDigestOf(scriptCode));
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

    /**
     * 由脚本内容算出一个稳定且<b>不会互相撞名</b>的实现名。
     * <p>
     * 早前是 {@code "script-" + scriptCode.hashCode()}，而 String.hashCode() 是 32 位
     * 且<b>可构造碰撞</b>（实测：{@code "Aa0"}/{@code "BB0"}、{@code "Aa1"}/{@code "BB1"}
     * 分别为 65520/65521，规律是 {@code "Aa"+i} 与 {@code "BB"+i} 全部同值）。
     * 后果链：
     * <ol>
     *   <li>两段不同脚本注册出<b>同名</b>的 ExtImplDefinition（registerImpl 走
     *       CopyOnWriteArrayList.add，不去重，两条都在）；</li>
     *   <li>{@code ExtRegistry.getActiveImplementation} 按 name 匹配 + {@code findFirst()}，
     *       于是被激活/路由到的是<b>先注册的那段</b>，后注册的永远选不中；</li>
     *   <li>CHAIN 类型下两段都会执行，SYNC/ASYNC 下静默选错。</li>
     * </ol>
     * 改用 SHA-256 前 8 字节的十六进制（16 个十六进制字符）：脚本内容不同则名字不同，
     * 名字也始终是纯十六进制（旧的 hashCode 是有符号 32 位，会拼出 {@code script--1178225722}
     * 这种双横线名字，日志/排查时很别扭）。
     * <p>
     * 注意：这里刻意<b>不</b>用 UUID.randomUUID()——那样每次启动名字都变，
     * {@code setActiveImpl} 的配置无法跨重启保持。内容寻址才是对的。
     */
    static String nameDigestOf(String scriptCode) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(scriptCode.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 在任何 JRE 上都存在；真缺了就不猜, 让它显式失败
            throw new IllegalStateException("SHA-256 not available", e);
        }
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
