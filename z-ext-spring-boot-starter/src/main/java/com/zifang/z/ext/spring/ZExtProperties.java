package com.zifang.z.ext.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * z-ext 装配配置项（前缀 {@code z.ext}）。
 */
@ConfigurationProperties(prefix = "z.ext")
public class ZExtProperties {

    /**
     * 是否装配嵌入式控制面（/api/ext/**）。
     * <p>
     * 宿主不愿对外暴露控制面时关掉，改由独立控制台经实例代理转发。
     */
    private boolean adminEnabled = true;

    /**
     * 控制面 HTTP 根路径。
     */
    private String adminBasePath = "/api/ext";

    /**
     * 实例标识（控制台聚合展示用）。缺省取 {@code spring.application.name}，
     * 再缺省取主机名。
     */
    private String instanceName;

    private final Async async = new Async();

    public boolean isAdminEnabled() {
        return adminEnabled;
    }

    public void setAdminEnabled(boolean adminEnabled) {
        this.adminEnabled = adminEnabled;
    }

    public String getAdminBasePath() {
        return adminBasePath;
    }

    public void setAdminBasePath(String adminBasePath) {
        this.adminBasePath = adminBasePath;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public Async getAsync() {
        return async;
    }

    /**
     * 异步执行相关配置。
     */
    public static class Async {

        /**
         * 是否为 ASYNC 类型扩展点启用真异步执行。
         * <p>
         * 默认关闭：关闭时 ASYNC 与 SYNC 一样同步执行（框架早期行为），
         * 避免存量扩展点被静默改成 fire-and-forget。
         */
        private boolean enabled = false;

        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 1000;
        private String threadNamePrefix = "z-ext-async-";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }
    }
}
