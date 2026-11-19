package com.zifang.z.ext.preset;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;

/**
 * 预置扩展点的程序化注册器。
 * <p>
 * <b>为什么不用注解扫描</b>：预置点属于框架自带能力，宿主引入 starter 就该有，
 * 不该要求业务在启动类上再写一行 {@code @EnableExt} —— 那会让「零侵入」名不副实。
 * 这里直接写注册表，宿主一行代码都不用加。
 * <p>
 * <b>幂等</b>：重复调用不会重复注册，也不会覆盖宿主/控制面已有的同名实现 ——
 * 名字冲突时保留先到的那一个（控制面注入的同名实现优先级更高，因为它是有意为之）。
 */
public final class PresetRegistrar {

    private PresetRegistrar() {
    }

    /**
     * 确保三个预置点与其默认实现都在注册表里。
     * <p>
     * 任何时候都可以安全调用：已注册则原地不动。
     */
    public static synchronized void ensureRegistered() {
        registerPoint(PresetPoints.GATE, PresetGateExtPoint.class,
                "预置闸门：运行期由控制面决定放行或拒绝");
        registerImpl(PresetPoints.GATE, "platform-default", PresetGateDefaultExtPoint.class,
                "默认放行（不接管）");

        registerPoint(PresetPoints.ADVICE, PresetAdviceExtPoint.class,
                "预置环绕：包住既有调用，可改写结果或中断");
        registerImpl(PresetPoints.ADVICE, "platform-default", PresetAdviceDefaultExtPoint.class,
                "默认放行（不接管）");

        registerPoint(PresetPoints.EVENT, PresetEventExtPoint.class,
                "预置通知：运行期事件出口（观测/告警/审计）");
        registerImpl(PresetPoints.EVENT, "platform-default", PresetEventDefaultExtPoint.class,
                "默认空实现（不产生噪音）");
    }

    private static void registerPoint(String point, Class<?> interfaceClass, String description) {
        if (ExtRegistry.getPoint(point) != null) {
            return;
        }
        ExtPointDefinition definition = new ExtPointDefinition();
        definition.setPoint(point);
        definition.setInterfaceClass(interfaceClass);
        definition.setType(ExtType.SYNC);
        definition.setDescription(description);
        definition.setVersion("1.0.0");
        ExtRegistry.registerPoint(definition);
    }

    private static void registerImpl(String point, String name, Class<?> implClass, String description) {
        for (ExtImplDefinition existing : ExtRegistry.getImplementations(point)) {
            if (name.equals(existing.getName())) {
                // 已有同名实现（宿主或控制面注册的）⇒ 保留，不覆盖
                return;
            }
        }
        ExtImplDefinition definition = new ExtImplDefinition(point, name, implClass);
        definition.setType(ExtImplType.PLATFORM);
        definition.setDescription(description);
        definition.setEnabled(true);
        definition.setOrder(0);
        ExtRegistry.registerImpl(definition);
    }
}
