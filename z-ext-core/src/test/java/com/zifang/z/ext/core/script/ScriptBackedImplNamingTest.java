package com.zifang.z.ext.core.script;

import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脚本扩展实现的命名与撞名闸。
 * <p>
 * 病灶：{@code ScriptBackedExtImpl} 原先用 {@code "script-" + scriptCode.hashCode()} 当实现名。
 * String.hashCode() 是 32 位且可构造碰撞（实测 "Aa0" 与 "BB0" 同为 65520，
 * 规律是 "Aa"+i 与 "BB"+i 全部同值）。撞名后
 * {@code ExtRegistry.getActiveImplementation} 的「按 name + findFirst」会静默选中先注册的那个，
 * 后注册的永远选不中。
 */
class ScriptBackedImplNamingTest {

    @BeforeEach
    void setUp() {
        ExtRegistry.clear();
    }

    // ---------- 命名 ----------

    @Test
    void knownHashCollidingScriptsGetDifferentNames() {
        // 这两个 String.hashCode() 严格相等（实测都是 65520），旧实现下名字会一模一样
        String x = "Aa0";
        String y = "BB0";
        assertEquals(x.hashCode(), y.hashCode(),
                "前提: 这两个脚本的 hashCode 必须真的相等，否则本测试没有意义");
        assertNotEquals(ScriptBackedExtImpl.nameDigestOf(x),
                ScriptBackedExtImpl.nameDigestOf(y),
                "hashCode 相同的两段脚本必须得到不同的实现名");
    }

    @Test
    void nameIsStableAcrossCalls() {
        // 内容寻址: 同样内容两次算出同样名字, 这样 setActiveImpl 的配置能跨重启保持
        String code = "return ctx.amount * 2";
        assertEquals(ScriptBackedExtImpl.nameDigestOf(code),
                ScriptBackedExtImpl.nameDigestOf(code));
    }

    @Test
    void nameIsPureHexWithoutLeadingMinus() {
        // 旧的 hashCode 是有符号 32 位, 会拼出 script--1178225722 这种双横线名字
        String name = ScriptBackedExtImpl.nameDigestOf("return ctx.a - 1");
        assertTrue(name.matches("[0-9a-f]{16}"),
                "实现名后缀应为 16 位小写十六进制，实际: " + name);
    }

    @Test
    void differentScriptsGetDifferentNamesInBulk() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            names.add(ScriptBackedExtImpl.nameDigestOf("script body #" + i));
        }
        assertEquals(2000, names.size(), "2000 段不同脚本应得到 2000 个不同名字");
    }

    // ---------- 撞名闸 ----------

    @Test
    void registerImplRejectsDuplicateName() {
        ExtImplDefinition a = def("myPoint", "sameName", String.class);
        ExtImplDefinition b = def("myPoint", "sameName", Integer.class);

        ExtRegistry.registerImpl(a);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ExtRegistry.registerImpl(b));
        assertTrue(ex.getMessage().contains("sameName"),
                "报错里应点名冲突的实现名: " + ex.getMessage());
    }

    @Test
    void duplicateNameDoesNotPolluteList() {
        ExtImplDefinition a = def("myPoint", "sameName", String.class);
        ExtImplDefinition b = def("myPoint", "sameName", Integer.class);
        ExtRegistry.registerImpl(a);
        try {
            ExtRegistry.registerImpl(b);
        } catch (IllegalArgumentException ignored) {
            // 预期抛出
        }
        List<ExtImplDefinition> impls = ExtRegistry.getImplementations("myPoint");
        assertEquals(1, impls.size(),
                "被拒绝的注册绝不能留在注册表里，实际 " + impls.size() + " 条");
    }

    @Test
    void sameNameSameClassIsIdempotent() {
        // 容器里同一个 bean 被扫描到两次不应炸
        ExtRegistry.registerImpl(def("myPoint", "impl", String.class));
        ExtRegistry.registerImpl(def("myPoint", "impl", String.class));
        assertEquals(1, ExtRegistry.getImplementations("myPoint").size());
    }

    @Test
    void differentNamesCoexist() {
        ExtRegistry.registerImpl(def("myPoint", "implA", String.class));
        ExtRegistry.registerImpl(def("myPoint", "implB", Integer.class));
        assertEquals(2, ExtRegistry.getImplementations("myPoint").size());
    }

    @Test
    void sameNameOnDifferentPointsIsAllowed() {
        // 撞名闸只应作用于同一个 point 内部
        ExtRegistry.registerImpl(def("pointA", "same", String.class));
        ExtRegistry.registerImpl(def("pointB", "same", Integer.class));
        assertEquals(1, ExtRegistry.getImplementations("pointA").size());
        assertEquals(1, ExtRegistry.getImplementations("pointB").size());
    }

    @Test
    void activatedImplIsUnambiguousAfterRegisteringMany() {
        // 端到端：注册 3 个不同名实现，激活其中一个，必须精确命中它
        ExtRegistry.registerImpl(def("p", "implA", String.class));
        ExtRegistry.registerImpl(def("p", "implB", Integer.class));
        ExtRegistry.registerImpl(def("p", "implC", Long.class));

        ExtRegistry.setActiveImpl("p", "implB");
        ExtImplDefinition active = ExtRegistry.getActiveImplementation("p");
        assertTrue(active != null && "implB".equals(active.getName()),
                "激活 implB 必须精确命中它");
    }

    @Test
    void scriptBackedProxyRegistrationIsUniquePerCode() {
        ScriptBackedExtImpl bridge = new ScriptBackedExtImpl(
                (code, dsl, method, args) -> "ok");
        Class<?> iface = NamingProbe.class;

        bridge.createProxy("scriptPoint", "Aa0", "groovy", iface);
        bridge.createProxy("scriptPoint", "BB0", "groovy", iface);

        List<ExtImplDefinition> impls = ExtRegistry.getImplementations("scriptPoint");
        assertEquals(2, impls.size(), "两段不同脚本都应注册成功");
        Set<String> names = new HashSet<>();
        for (ExtImplDefinition d : impls) {
            names.add(d.getName());
        }
        assertEquals(2, names.size(), "两段 hashCode 相同的脚本也必须得到不同名字: " + names);
    }

    // ---------- helpers ----------

    interface NamingProbe {
        String ping();
    }

    private static ExtImplDefinition def(String point, String name, Class<?> implClass) {
        ExtImplDefinition d = new ExtImplDefinition();
        d.setPoint(point);
        d.setName(name);
        d.setImplClass(implClass);
        d.setType(com.zifang.z.ext.annotation.ExtImplType.CUSTOM);
        d.setEnabled(true);
        d.setOrder(0);
        return d;
    }
}
