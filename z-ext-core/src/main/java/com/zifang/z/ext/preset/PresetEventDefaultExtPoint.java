package com.zifang.z.ext.preset;

import com.zifang.z.ext.annotation.ExtImpl;

/**
 * 预置通知的默认实现：什么都不做。
 * <p>
 * 空实现是刻意的 —— 观测型通知的默认代价应当是零。
 */
@ExtImpl(point = PresetPoints.EVENT,
        name = "platform-default",
        description = "默认空实现（不产生噪音）",
        type = com.zifang.z.ext.annotation.ExtImplType.PLATFORM,
        order = 0)
public class PresetEventDefaultExtPoint implements PresetEventExtPoint {

    @Override
    public void publish(String event, java.util.Map<String, Object> attrs) {
        // 默认不观测
    }
}
