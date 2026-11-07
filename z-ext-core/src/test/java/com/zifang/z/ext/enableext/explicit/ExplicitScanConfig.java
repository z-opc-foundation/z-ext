package com.zifang.z.ext.enableext.explicit;

import com.zifang.z.ext.annotation.EnableExt;
import com.zifang.z.ext.annotation.ExtImpl;
import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtPoint;
import com.zifang.z.ext.annotation.ExtType;
import org.springframework.context.annotation.Configuration;

/**
 * 显式 basePackages 分支的集成测试夹具。
 * <p>
 * 配置类与它的 {@code @ExtPoint} / {@code @ExtImpl} 都放在本包内，{@code @EnableExt}
 * 显式把 basePackages 指向本包，扫描范围与另一个分支的物理包完全隔离。
 */
@Configuration
@EnableExt(basePackages = "com.zifang.z.ext.enableext.explicit")
public class ExplicitScanConfig {

    @ExtPoint(value = "explicit.greeting", type = ExtType.SYNC, description = "显式包扫描的扩展点")
    public interface GreetingPoint {
        String greet(String who);
    }

    @ExtImpl(point = "explicit.greeting", name = "english", type = ExtImplType.PLATFORM,
            description = "英文实现", order = 5, weight = 7)
    public static class EnglishGreeting implements GreetingPoint {
        @Override
        public String greet(String who) {
            return "hello " + who;
        }
    }
}
