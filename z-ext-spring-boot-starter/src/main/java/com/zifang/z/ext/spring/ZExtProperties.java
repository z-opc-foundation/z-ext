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

    private final Preset preset = new Preset();

    private final Console console = new Console();

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
     * 预置埋点相关配置。
     */
    public Preset getPreset() {
        return preset;
    }

    /**
     * 向控制台登记本实例。
     */
    public Console getConsole() {
        return console;
    }

    /**
     * 实例向控制台自登记。
     * <p>
     * 控制面要能「看见并操控」实例，前提是知道实例在哪儿。
     * 这条通道让实例启动时把自己登记到控制台、退出时注销 ——
     * 不依赖任何中间注册中心，因此控制台与实例之间只要网络通就能用。
     */
    public static class Console {

        /**
         * 是否开启自登记。
         * <p>
         * 默认关：登记地址是环境相关的，交给部署侧显式开启。
         */
        private boolean enabled = false;

        /**
         * 控制台地址（如 {@code http://192.168.31.136:8888}）。
         */
        private String url;

        /**
         * 实例名（缺省取 {@code spring.application.name}）。
         */
        private String instanceName;

        /**
         * 对外暴露的地址覆盖（缺省按本机站点地址 + 端口推）。
         */
        private String baseUrl;

        /**
         * 连接/读超时（毫秒）。
         */
        private int timeoutMs = 3000;

        /**
         * 登记令牌（与控制台侧 z.ext.console.reg-token 对暗号；控制台未配置或对不上即拒绝登记）。
         * <p>
         * 只走环境变量注入（ZEXT_CONSOLE_REG_TOKEN），不落仓 ——
         * 登记端点若开放无凭据写入，攻击者可借控制台的代理透传拿到操作员令牌。
         */
        private String regToken;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getInstanceName() {
            return instanceName;
        }

        public void setInstanceName(String instanceName) {
            this.instanceName = instanceName;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getRegToken() {
            return regToken;
        }

        public void setRegToken(String regToken) {
            this.regToken = regToken;
        }
    }

    /**
     * 预置埋点。
     * <p>
     * 预置埋点是框架自带的「零业务侵入」落点：引依赖即注册三个可被控制面接管的点。
     */
    public static class Preset {

        /**
         * 是否启用预置埋点。
         */
        private boolean enabled = true;

        private final Http http = new Http();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Http getHttp() {
            return http;
        }

        /**
         * HTTP 闸门落点。
         */
        public static class Http {

            /**
             * 是否把闸门接到 HTTP 请求上。
             */
            private boolean enabled = true;

            /**
             * 拒绝时返回的状态码。
             */
            private int denyStatus = 503;

            /**
             * 闸门实现异常时是否放行。
             * <p>
             * 默认放行：控制面是运维手段，它坏了不该把业务一起带下去。
             */
            private boolean failOpen = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getDenyStatus() {
                return denyStatus;
            }

            public void setDenyStatus(int denyStatus) {
                this.denyStatus = denyStatus;
            }

            public boolean isFailOpen() {
                return failOpen;
            }

            public void setFailOpen(boolean failOpen) {
                this.failOpen = failOpen;
            }
        }
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
