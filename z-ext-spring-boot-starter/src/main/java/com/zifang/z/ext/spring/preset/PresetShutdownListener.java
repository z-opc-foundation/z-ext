package com.zifang.z.ext.spring.preset;

import com.zifang.z.ext.preset.PresetEventExtPoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

/**
 * 关闭落点：容器关闭时发一个事件（优雅下线通知、流量摘除钩子）。
 */
public class PresetShutdownListener implements ApplicationListener<ContextClosedEvent> {

    private static final Logger log = LogManager.getLogger(PresetShutdownListener.class);

    /** 通知点。 */
    private final PresetEventExtPoint events;

    /** 应用名。 */
    private final String appName;

    public PresetShutdownListener(PresetEventExtPoint events, String appName) {
        this.events = events;
        this.appName = appName;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        try {
            events.publish("app.shutdown", java.util.Collections.singletonMap("app", appName));
        } catch (Exception e) {
            log.debug("预置通知点异常，忽略：err={}", e.toString());
        }
    }
}
