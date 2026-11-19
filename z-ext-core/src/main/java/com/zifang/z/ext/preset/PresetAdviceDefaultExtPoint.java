package com.zifang.z.ext.preset;

import com.zifang.z.ext.annotation.ExtImpl;

import java.util.Map;

/**
 * 预置环绕的默认实现：原样放行。
 * <p>
 * 调用原动作并把结果原样返回 —— 没有控制面介入时，环绕等价于不存在。
 */
@ExtImpl(point = PresetPoints.ADVICE,
        name = "platform-default",
        description = "默认放行（不接管）",
        type = com.zifang.z.ext.annotation.ExtImplType.PLATFORM,
        order = 0)
public class PresetAdviceDefaultExtPoint implements PresetAdviceExtPoint {

    @Override
    public Object around(String event, Map<String, Object> attrs, ProceedAction action) throws Throwable {
        return action.proceed();
    }
}
