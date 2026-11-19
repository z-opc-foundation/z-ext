package com.zifang.z.ext.admin.instance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Z-Config 命名面实例源：从 Z-Config server 的 /api/naming 拉取服务实例。
 * <p>
 * 拉取结果带短 TTL 缓存；z-config 不可达时回退上次成功快照，保证控制台在
 * 注册中心抖动时仍能转发。手工登记（{@link ExtInstanceRegistry}）作为兜底与
 * 覆写层继续生效。
 */
@Component
public class ZConfigInstanceSource {

    private static final long CACHE_TTL_MS = 10_000;

    @Value("${z.ext.console.zconfig.server-addr:}")
    private String serverAddr;

    private final RestClient restClient;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile long lastRefreshAt;
    private volatile boolean lastRefreshFailed;

    public ZConfigInstanceSource() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean enabled() {
        return serverAddr != null && !serverAddr.isBlank();
    }

    /** 返回 serviceName → baseUrl 快照（带 TTL 缓存；失败时回退旧快照）。 */
    public Map<String, String> snapshot() {
        if (!enabled()) {
            return Map.of();
        }
        long now = System.currentTimeMillis();
        if (now - lastRefreshAt > CACHE_TTL_MS) {
            refresh();
        }
        return cache;
    }

    /** 最近一次拉取是否失败（用于实例表上标注数据源健康状态）。 */
    public boolean lastRefreshFailed() {
        return lastRefreshFailed;
    }

    @SuppressWarnings("unchecked")
    private synchronized void refresh() {
        if (System.currentTimeMillis() - lastRefreshAt <= CACHE_TTL_MS) {
            return;
        }
        try {
            Map<String, Object> servicesResp = restClient.get()
                    .uri(serverAddr + "/api/naming/listServices")
                    .retrieve().body(Map.class);
            List<Map<String, Object>> services = data(servicesResp);
            Map<String, String> fresh = new LinkedHashMap<>();
            for (Map<String, Object> svc : services) {
                Object name = svc.get("serviceName");
                if (name == null || name.toString().isBlank()) {
                    continue;
                }
                Map<String, Object> instancesResp = restClient.get()
                        .uri(serverAddr + "/api/naming/getAllInstances?serviceName=" + name)
                        .retrieve().body(Map.class);
                List<Map<String, Object>> instances = data(instancesResp);
                for (Map<String, Object> inst : instances) {
                    Object ip = inst.get("ip");
                    Object port = inst.get("port");
                    if (ip == null || port == null) {
                        continue;
                    }
                    fresh.putIfAbsent(name.toString(), "http://" + ip + ":" + port);
                }
            }
            cache.clear();
            cache.putAll(fresh);
            lastRefreshFailed = false;
        } catch (Exception e) {
            lastRefreshFailed = true;
        }
        lastRefreshAt = System.currentTimeMillis();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> data(Map<String, Object> resp) {
        if (resp == null || !Boolean.TRUE.equals(resp.get("success")) || resp.get("data") == null) {
            return List.of();
        }
        Object data = resp.get("data");
        return data instanceof List ? (List<Map<String, Object>>) data : List.of();
    }
}
