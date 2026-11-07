package com.zifang.z.ext;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtScanner;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.enableext.defaultpkg.DefaultScanConfig;
import com.zifang.z.ext.enableext.explicit.ExplicitScanConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code @EnableExt} 扫描链路的集成级测试：真正起一个 Spring 容器，而不只是手工调
 * {@link ExtRegistry}。修复前 {@code ExtScanner} 没有 {@code setBasePackages}，
 * 而 {@code ExtRegistrar} 用 {@code addPropertyValue} 写该属性，BeanFactoryPostProcessor
 * 在容器 refresh 极早期实例化 ⇒ 一用 {@code @EnableExt} 就抛
 * {@code NotWritablePropertyException}，整个上下文起不来。
 * <p>
 * 两个用例分别覆盖：显式 basePackages、以及不指定时默认取「注解所在类的包」；
 * 每个用例都断言容器 refresh 不抛异常，且 {@link ExtRegistry} 里确实有扫描到的
 * {@code @ExtPoint} / {@code @ExtImpl} 定义（连注解属性一起校验）。
 * <p>
 * {@link ExtRegistry} 是 JVM 级静态状态，故用 @BeforeEach/@AfterEach 双清，
 * 与同 JVM 下的其它测试类隔离。
 */
class EnableExtIntegrationTest {

    @BeforeEach
    void clearRegistryBefore() {
        ExtRegistry.clear();
    }

    @AfterEach
    void clearRegistryAfter() {
        ExtRegistry.clear();
    }

    @Test
    @DisplayName("显式 basePackages：容器启动不崩，且真的扫到 @ExtPoint/@ExtImpl")
    void explicitBasePackagesStartsContextAndScansPointsAndImpls() {
        // 构造即 refresh；若注入链路仍缺 setter，这里会直接抛 BeanCreationException/NotWritablePropertyException，
        // 测试随之判红 —— 这就是「启动不崩」的证明。
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(ExplicitScanConfig.class)) {

            assertNotNull(ctx.getBean(ExtScanner.class), "extScanner 应作为 BeanFactoryPostProcessor 被装配");

            // 证明注入的数组没有退化成逗号连接的字符串：BeanDefinition 里必须是 String[]。
            BeanDefinition bd = ctx.getBeanFactory().getBeanDefinition("extScanner");
            PropertyValue pv = bd.getPropertyValues().getPropertyValue("basePackages");
            assertNotNull(pv, "extScanner 的 BeanDefinition 应带 basePackages 属性");
            Object raw = pv.getValue();
            assertTrue(raw instanceof String[],
                    "basePackages 应以 String[] 注入，实际类型：" + (raw == null ? null : raw.getClass()));
            assertArrayEquals(new String[]{"com.zifang.z.ext.enableext.explicit"}, (String[]) raw);

            // 扫到的扩展点，连注解属性一起核对
            ExtPointDefinition point = ExtRegistry.getPoint("explicit.greeting");
            assertNotNull(point, "扫描后注册表里应有 @ExtPoint 定义");
            assertEquals(ExplicitScanConfig.GreetingPoint.class, point.getInterfaceClass());
            assertEquals(ExtType.SYNC, point.getType());
            assertEquals("显式包扫描的扩展点", point.getDescription());
            assertEquals("1.0.0", point.getVersion());

            // 扫到的实现，连注解属性一起核对
            List<ExtImplDefinition> impls = ExtRegistry.getImplementations("explicit.greeting");
            assertEquals(1, impls.size(), "应恰好扫到 1 个 @ExtImpl");
            ExtImplDefinition impl = impls.get(0);
            assertEquals("english", impl.getName());
            assertEquals(ExplicitScanConfig.EnglishGreeting.class, impl.getImplClass());
            assertEquals(ExtImplType.PLATFORM, impl.getType());
            assertEquals("英文实现", impl.getDescription());
            assertTrue(impl.isEnabled());
            assertEquals(5, impl.getOrder());
            assertEquals(7, impl.getWeight());

            assertNotNull(ExtRegistry.getPointByInterface(ExplicitScanConfig.GreetingPoint.class),
                    "扫描注册的扩展点应能按接口类反查到");
        }
    }

    @Test
    @DisplayName("未指定 basePackages：默认取注解所在类的包，启动不崩且扫到本包扩展点")
    void defaultBasePackagesFallsBackToAnnotatedClassPackage() {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(DefaultScanConfig.class)) {

            assertNotNull(ctx.getBean(ExtScanner.class));

            ExtPointDefinition point = ExtRegistry.getPoint("default.farewell");
            assertNotNull(point, "默认分支应扫到注解所在包内的 @ExtPoint");
            assertEquals(DefaultScanConfig.FarewellPoint.class, point.getInterfaceClass());
            assertEquals(ExtType.ASYNC, point.getType());
            assertEquals("默认包扫描的扩展点", point.getDescription());
            assertEquals("2.3.4", point.getVersion());

            List<ExtImplDefinition> impls = ExtRegistry.getImplementations("default.farewell");
            assertEquals(1, impls.size());
            ExtImplDefinition impl = impls.get(0);
            assertEquals("casual", impl.getName());
            assertEquals(DefaultScanConfig.CasualFarewell.class, impl.getImplClass());
            assertEquals(ExtImplType.CUSTOM, impl.getType());
            assertEquals(9, impl.getOrder());
            assertEquals(3, impl.getWeight());

            // 默认分支的扫描根只能是「注解所在类的包」，不该把另一个分支所在包也扫进来
            assertNull(ExtRegistry.getPoint("explicit.greeting"),
                    "默认分支不应扫到其它包的扩展点");
        }
    }

    @Test
    @DisplayName("setBasePackages 做防御性拷贝，注入数组不与调用方共享引用")
    void setBasePackagesDefensivelyCopiesArray() throws Exception {
        String[] packages = {"com.a", "com.b"};
        ExtScanner scanner = new ExtScanner();
        scanner.setBasePackages(packages);

        // 外部改动原数组不得影响 scanner 内部的扫描范围
        packages[0] = "com.mutated";

        Field field = ExtScanner.class.getDeclaredField("basePackages");
        field.setAccessible(true);
        String[] stored = (String[]) field.get(scanner);
        assertNotSame(packages, stored, "注入数组应被拷贝，而非共享同一引用");
        assertArrayEquals(new String[]{"com.a", "com.b"}, stored);
    }
}
