package com.zifang.z.ext.spring.preset;

import com.zifang.z.ext.preset.PresetEventExtPoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * 生命周期落点：启动就绪发一个事件。
 * <p>
 * 控制面可以给它挂实现（就绪探针、启动耗时上报），
 * 不需要业务在启动回调里写任何代码。
 */
public class PresetLifecycleListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LogManager.getLogger(PresetLifecycleListener.class);

    /** 通知点。 */
    private final PresetEventExtPoint events;

    /** 应用名。 */
    private final String appName;

    public PresetLifecycleListener(PresetEventExtPoint events, String appName) {
        this.events = events;
        this.appName = appName;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        publish("app.ready");
    }

    private void publish(String event) {
        try {
            events.publish(event, java.util.Collections.singletonMap("app", appName));
        } catch (Exception e) {
            log.debug("预置通知点异常，忽略：event={} err={}", event, e.toString());
        }
    }
}
