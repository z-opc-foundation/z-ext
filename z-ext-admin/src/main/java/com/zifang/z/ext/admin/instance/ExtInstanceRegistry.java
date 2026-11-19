package com.zifang.z.ext.admin.instance;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 已注册业务实例表：instanceName → baseUrl。
 * <p>
 * 数据源两路合并：
 * <ul>
 *   <li>z-config 命名面（{@link ZConfigInstanceSource}）——服务自注册的主来源；</li>
 *   <li>手工登记（{@link #register}）——兜底与覆写（同名时手工登记优先）。</li>
 * </ul>
 * Agent 模式下控制台按这里登记的地址向实例嵌入式控制面转发操作。
 */
@Component
public class ExtInstanceRegistry {

    private final Map<String, String> instances = new ConcurrentHashMap<>();
    private final ZConfigInstanceSource zConfigSource;

    public ExtInstanceRegistry(ZConfigInstanceSource zConfigSource) {
        this.zConfigSource = zConfigSource;
    }

    public void register(String name, String baseUrl) {
        validateName(name);
        validateBaseUrl(baseUrl);
        instances.put(name, stripTrailingSlash(baseUrl));
    }

    public void unregister(String name) {
        instances.remove(name);
    }

    public String baseUrlOf(String name) {
        String base = instances.get(name);
        if (base != null) {
            return base;
        }
        String fromZConfig = zConfigSource.snapshot().get(name);
        if (fromZConfig != null) {
            return fromZConfig;
        }
        throw new IllegalArgumentException("未注册的实例：" + name);
    }

    public List<Map<String, String>> list() {
        Map<String, Map<String, String>> merged = new LinkedHashMap<>();
        zConfigSource.snapshot().forEach((name, baseUrl) ->
                merged.put(name, sourceEntry(name, baseUrl, "zconfig")));
        instances.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> merged.put(e.getKey(), sourceEntry(e.getKey(), e.getValue(), "manual")));
        return new ArrayList<>(merged.values());
    }

    /** z-config 命名面最近一次拉取是否失败（false = 未启用或最近拉取正常）。 */
    public boolean zConfigDegraded() {
        return zConfigSource.enabled() && zConfigSource.lastRefreshFailed();
    }

    private static Map<String, String> sourceEntry(String name, String baseUrl, String source) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("baseUrl", baseUrl);
        entry.put("source", source);
        return entry;
    }

    private void validateName(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("实例名只允许字母数字下划线中划线（1-64 位）：" + name);
        }
    }

    private void validateBaseUrl(String baseUrl) {
        if (baseUrl == null || !(baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("实例地址必须是 http(s) URL：" + baseUrl);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
