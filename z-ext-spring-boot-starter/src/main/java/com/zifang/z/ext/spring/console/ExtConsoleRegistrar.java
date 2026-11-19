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
 */
public class ExtConsoleRegistrar implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LogManager.getLogger(ExtConsoleRegistrar.class);

    /** 控制台地址。 */
    private final String consoleUrl;

    /** 实例名。 */
    private final String instanceName;

    /** 对外地址（可空 ⇒ 按本机地址 + 端口推）。 */
    private final String baseUrlOverride;

    /** 超时（毫秒）。 */
    private final int timeoutMs;

    /** 登记上去的实例名（注销时用）。 */
    private volatile String registeredName;

    public ExtConsoleRegistrar(String consoleUrl, String instanceName, String baseUrlOverride, int timeoutMs) {
        this.consoleUrl = trimTrailingSlash(consoleUrl);
        this.instanceName = instanceName;
        this.baseUrlOverride = baseUrlOverride;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        register(event.getWebServer().getPort());
    }

    /**
     * 登记到控制台。
     *
     * @param port 本实例对外端口
     */
    void register(int port) {
        String baseUrl = baseUrlOverride != null && !baseUrlOverride.isBlank()
                ? trimTrailingSlash(baseUrlOverride)
                : "http://" + localAddress() + ":" + port;
        String name = instanceName == null || instanceName.isBlank() ? "unknown" : instanceName.trim();
        try {
            String response = call("POST", "/api/ext/instances",
                    "{\"name\":\"" + escape(name) + "\",\"baseUrl\":\"" + escape(baseUrl) + "\"}");
            registeredName = name;
            log.info("实例已向控制台登记：name={} baseUrl={} 控制台={} resp={}", name, baseUrl, consoleUrl, response);
        } catch (Exception e) {
            log.warn("实例向控制台登记失败（不影响启动）：name={} baseUrl={} 控制台={} err={}",
                    name, baseUrl, consoleUrl, e.toString());
        }
    }

    /**
     * 容器关闭时注销。
     */
    public void unregister() {
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
