package com.zifang.z.ext.enableext.defaultpkg;

import com.zifang.z.ext.annotation.EnableExt;
import com.zifang.z.ext.annotation.ExtImpl;
import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtPoint;
import com.zifang.z.ext.annotation.ExtType;
import org.springframework.context.annotation.Configuration;

/**
 * 默认 basePackages 分支的集成测试夹具。
 * <p>
 * {@code @EnableExt} 不写 basePackages，{@link com.zifang.z.ext.annotation.ExtRegistrar}
 * 会取「注解所在类的包」（即本包）作为扫描根。本包与显式分支的物理包隔离，
 * 可据此断言默认分支既扫到了本包扩展点，又没有把别的包的扩展点扫进来。
 */
@Configuration
@EnableExt
public class DefaultScanConfig {

    @ExtPoint(value = "default.farewell", type = ExtType.ASYNC,
            description = "默认包扫描的扩展点", version = "2.3.4")
    public interface FarewellPoint {
        String bye(String who);
    }

    @ExtImpl(point = "default.farewell", name = "casual", type = ExtImplType.CUSTOM,
            description = "随意实现", order = 9, weight = 3, enabled = true)
    public static class CasualFarewell implements FarewellPoint {
        @Override
        public String bye(String who) {
            return "bye " + who;
        }
    }
}
