package com.zifang.z.ext.preset;

import com.zifang.z.ext.annotation.ExtImpl;

import java.util.Map;

/**
 * 预置闸门的默认实现：恒放行。
 * <p>
 * 它是「什么都不接管」的基线 —— 控制面不介入时，业务行为与没有 z-ext 时完全一致。
 */
@ExtImpl(point = PresetPoints.GATE,
        name = "platform-default",
        description = "默认放行（不接管）",
        type = com.zifang.z.ext.annotation.ExtImplType.PLATFORM,
        order = 0)
public class PresetGateDefaultExtPoint implements PresetGateExtPoint {

    @Override
    public boolean allow(String event, Map<String, Object> attrs) {
        return true;
    }

    @Override
    public String denyMessage(String event, Map<String, Object> attrs) {
        return null;
    }
}
