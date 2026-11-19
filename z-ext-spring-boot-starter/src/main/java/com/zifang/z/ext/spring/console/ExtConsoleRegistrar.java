package com.zifang.z.ext.spring.console;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 实例向控制台自登记 —— 让「自动可被操控」不依赖任何中间注册中心。
 * <p>
 * 启动就绪后把自己登记到控制台（{@code POST /api/ext/instances}），
 * 容器关闭时注销（{@code DELETE /api/ext/instances/{name}}）：
 * 控制台随即就能看到这个实例、列出它的扩展点、切换它的实现。
 * <p>
 * <b>为什么不用注册中心</b>：注册中心通常在 SSO 闸门内（人走的通道），
 * 而服务之间的机器通道要另开一套凭据才能走通 —— 通道一多，「谁能看到谁」就说不清了。
 * 这里走一条直连通道：控制台与实例之间网络可达即可，登记信息只含服务名与地址。
 * <p>
 * <b>失败不拖挂启动</b>：控制台没起来、地址写错、网络不通，都只记日志。
 * <p>
 * <b>登记失败会退避重试</b>：实例与控制台不一定同时起来 —— 控制台晚起时，
 * 首登拿连接拒绝是常态，不重试就等于这个实例在控制台里永远缺席（除非重启）。
 * 重试跑在守护线程上，不占启动线程，也不拖慢就绪。
 */
public class ExtConsoleRegistrar implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LogManager.getLogger(ExtConsoleRegistrar.class);

    /** 默认退避间隔（毫秒）：控制台晚起/网络抖动时的重试节奏。 */
    static final long[] DEFAULT_RETRY_DELAYS_MS = {5000L, 15000L, 30000L, 60000L};

    /** 控制台地址。 */
    private final String consoleUrl;

    /** 实例名。 */
    private final String instanceName;

    /** 对外地址（可空 ⇒ 按本机地址 + 端口推）。 */
    private final String baseUrlOverride;

    /** 超时（毫秒）。 */
    private final int timeoutMs;

    /** 登记令牌（空则不带；控制台未配置同款令牌时登记会被拒）。 */
    private final String regToken;

    /** 重试退避间隔（毫秒）。 */
    private final long[] retryDelaysMs;

    /** 登记上去的实例名（注销时用）。 */
    private volatile String registeredName;

    /** 容器已关闭（停止重试）。 */
    private volatile boolean closed;

    /** 重试调度器（首次失败时懒建）。 */
    private volatile ScheduledExecutorService scheduler;

    public ExtConsoleRegistrar(String consoleUrl, String instanceName, String baseUrlOverride, int timeoutMs) {
        this(consoleUrl, instanceName, baseUrlOverride, timeoutMs, null);
    }

    public ExtConsoleRegistrar(String consoleUrl, String instanceName, String baseUrlOverride,
                               int timeoutMs, String regToken) {
        this(consoleUrl, instanceName, baseUrlOverride, timeoutMs, regToken, DEFAULT_RETRY_DELAYS_MS);
    }

    public ExtConsoleRegistrar(String consoleUrl, String instanceName, String baseUrlOverride,
                               int timeoutMs, String regToken, long[] retryDelaysMs) {
        this.consoleUrl = trimTrailingSlash(consoleUrl);
        this.instanceName = instanceName;
        this.baseUrlOverride = baseUrlOverride;
        this.timeoutMs = timeoutMs;
        this.regToken = regToken;
        this.retryDelaysMs = retryDelaysMs == null ? new long[0] : retryDelaysMs.clone();
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        register(event.getWebServer().getPort());
    }

    /**
     * 登记到控制台；失败则安排退避重试。
     *
     * @param port 本实例对外端口
     */
    void register(int port) {
        String baseUrl = baseUrlOverride != null && !baseUrlOverride.isBlank()
                ? trimTrailingSlash(baseUrlOverride)
                : "http://" + localAddress() + ":" + port;
        String name = instanceName == null || instanceName.isBlank() ? "unknown" : instanceName.trim();
        if (attemptRegister(name, baseUrl)) {
            return;
        }
        scheduleRetry(name, baseUrl, 0);
    }

    /**
     * 登记一次；成功返回 true。
     */
    private boolean attemptRegister(String name, String baseUrl) {
        try {
            String response = call("POST", "/api/ext/instances",
                    "{\"name\":\"" + escape(name) + "\",\"baseUrl\":\"" + escape(baseUrl) + "\"}");
            registeredName = name;
            log.info("实例已向控制台登记：name={} baseUrl={} 控制台={} resp={}", name, baseUrl, consoleUrl, response);
            return true;
        } catch (Exception e) {
            log.warn("实例向控制台登记失败（不影响启动）：name={} baseUrl={} 控制台={} err={}",
                    name, baseUrl, consoleUrl, e.toString());
            return false;
        }
    }

    /**
     * 退避重试登记：失败一次排下一次，间隔逐次拉长；容器关闭即停。
     */
    private void scheduleRetry(String name, String baseUrl, int attempt) {
        if (closed) {
            return;
        }
        if (attempt >= retryDelaysMs.length) {
            log.warn("实例向控制台登记最终失败（控制台始终不可达），需重启实例或手动登记：name={} 控制台={}",
                    name, consoleUrl);
            return;
        }
        long delay = retryDelaysMs[attempt];
        try {
            scheduler().schedule(() -> {
                if (closed) {
                    return;
                }
                if (attemptRegister(name, baseUrl)) {
                    log.info("退避重试后登记成功：name={} baseUrl={} 控制台={}", name, baseUrl, consoleUrl);
                    return;
                }
                scheduleRetry(name, baseUrl, attempt + 1);
            }, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            log.debug("容器已关闭，停止登记重试：name={}", name);
        }
    }

    private synchronized ScheduledExecutorService scheduler() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "z-ext-console-registrar");
                thread.setDaemon(true);
                return thread;
            });
        }
        return scheduler;
    }

    /**
     * 容器关闭时注销。
     */
    public void unregister() {
        closed = true;
        ScheduledExecutorService current = scheduler;
        if (current != null) {
            current.shutdownNow();
        }
        String name = registeredName;
        if (name == null) {
            return;
        }
        registeredName = null;
        try {
            call("DELETE", "/api/ext/instances/" + name, null);
            log.info("实例已从控制台注销：name={}", name);
        } catch (Exception e) {
            log.warn("实例从控制台注销失败（可忽略，控制台侧有陈旧实例清理）：name={} err={}", name, e.toString());
        }
    }

    /**
     * 容器关闭事件监听（注销走这里，避免漏掉）。
     */
    public ApplicationListener<ContextClosedEvent> closeListener() {
        return event -> unregister();
    }

    /**
     * 调一次控制台接口。
     */
    private String call(String method, String path, String body) throws IOException {
        URL url = new URL(consoleUrl + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            if (regToken != null && !regToken.isBlank()) {
                connection.setRequestProperty("X-Reg-Token", regToken.trim());
            }
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (code >= 400) {
                throw new IOException("控制台返回 " + code + ": " + text);
            }
            return text;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 本机站点地址（优先 site-local 的 IPv4）。控制台要能连上本实例 ⇒ 不能用 127.0.0.1。
     */
    static String localAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address.isLoopbackAddress() || address instanceof java.net.Inet6Address) {
                        continue;
                    }
                    if (address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("解析本机地址失败，回落 127.0.0.1：{}", e.toString());
        }
        return "127.0.0.1";
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
