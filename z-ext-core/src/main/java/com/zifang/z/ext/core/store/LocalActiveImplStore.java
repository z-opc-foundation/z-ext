package com.zifang.z.ext.core.store;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内激活映射。
 * <p>
 * 这是 {@link ActiveImplStore} 的默认形态，等价于 z-ext 早期的静态表行为：
 * 激活状态只在本进程可见，切换即时生效，不依赖任何外部设施。
 */
public class LocalActiveImplStore implements ActiveImplStore {

    private final Map<String, String> activeImpls = new ConcurrentHashMap<>();

    @Override
    public void setActiveImpl(String point, String implName) {
        if (point == null) {
            throw new IllegalArgumentException("扩展点标识不能为空");
        }
        if (implName == null) {
            activeImpls.remove(point);
            return;
        }
        activeImpls.put(point, implName);
    }

    @Override
    public String getActiveImpl(String point) {
        return point == null ? null : activeImpls.get(point);
    }

    @Override
    public void removeActiveImpl(String point) {
        if (point != null) {
            activeImpls.remove(point);
        }
    }

    @Override
    public Map<String, String> getAll() {
        return new HashMap<>(activeImpls);
    }

    @Override
    public void clear() {
        activeImpls.clear();
    }
}
