package com.zifang.z.ext.core.admin;

import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 基于 {@link ExtRegistry} 的默认控制面实现。
 * <p>
 * 作用于本进程注册表，是嵌入式与 Agent 形态的共同底座：前者把它包进 controller，
 * 后者把它包进 RPC 服务。
 */
public class DefaultExtAdminProvider implements ExtAdminProvider {

    private final String instance;

    public DefaultExtAdminProvider() {
        this(defaultInstanceName());
    }

    public DefaultExtAdminProvider(String instance) {
        this.instance = instance;
    }

    public String getInstance() {
        return instance;
    }

    @Override
    public ExtSnapshot snapshot() {
        ExtSnapshot snapshot = new ExtSnapshot();
        snapshot.setInstance(instance);
        snapshot.setTimestamp(System.currentTimeMillis());

        List<ExtPointView> pointViews = new ArrayList<>();
        for (ExtPointDefinition pointDef : ExtRegistry.getAllPoints().values()) {
            pointViews.add(toView(pointDef));
        }
        pointViews.sort(Comparator.comparing(ExtPointView::getPoint, Comparator.nullsLast(Comparator.naturalOrder())));
        snapshot.setPoints(pointViews);
        return snapshot;
    }

    @Override
    public boolean activate(String point, String implName) {
        if (findImpl(point, implName) == null) {
            return false;
        }
        ExtRegistry.setActiveImpl(point, implName);
        return true;
    }

    @Override
    public boolean deactivate(String point) {
        if (ExtRegistry.getPoint(point) == null) {
            return false;
        }
        ExtRegistry.getActiveImplStore().removeActiveImpl(point);
        return true;
    }

    @Override
    public boolean setEnabled(String point, String implName, boolean enabled) {
        ExtImplDefinition impl = findImpl(point, implName);
        if (impl == null) {
            return false;
        }
        impl.setEnabled(enabled);
        return true;
    }

    @Override
    public boolean registerImpl(ExtImplDefinition definition) {
        if (definition == null || definition.getPoint() == null || definition.getName() == null) {
            return false;
        }
        try {
            ExtRegistry.registerImpl(definition);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private ExtImplDefinition findImpl(String point, String implName) {
        if (point == null || implName == null) {
            return null;
        }
        for (ExtImplDefinition impl : ExtRegistry.getImplementations(point)) {
            if (implName.equals(impl.getName())) {
                return impl;
            }
        }
        return null;
    }

    private ExtPointView toView(ExtPointDefinition pointDef) {
        ExtPointView view = new ExtPointView();
        view.setId(pointDef.getPoint());
        view.setPoint(pointDef.getPoint());
        view.setInterfaceClass(pointDef.getInterfaceClass() == null
                ? null : pointDef.getInterfaceClass().getName());
        view.setType(pointDef.getType() == null ? null : pointDef.getType().name());
        view.setDescription(pointDef.getDescription());
        view.setVersion(pointDef.getVersion());
        view.setActiveImpl(ExtRegistry.getActiveImpl(pointDef.getPoint()));

        String activeImplName = view.getActiveImpl();
        List<ExtImplView> implViews = new ArrayList<>();
        for (ExtImplDefinition impl : ExtRegistry.getImplementations(pointDef.getPoint())) {
            ExtImplView implView = new ExtImplView();
            implView.setId(impl.getPoint() + ":" + impl.getName());
            implView.setPoint(impl.getPoint());
            implView.setName(impl.getName());
            implView.setType(impl.getType() == null ? null : impl.getType().name());
            implView.setImplClass(impl.getImplClass() == null
                    ? null : impl.getImplClass().getName());
            implView.setDescription(impl.getDescription());
            implView.setOrder(impl.getOrder());
            implView.setWeight(impl.getWeight());
            implView.setEnabled(impl.isEnabled());
            implView.setCondition(impl.getCondition());
            implView.setRpcAddress(impl.getRpcAddress());
            implView.setRpcPort(impl.getRpcPort());
            implView.setActive(impl.getName() != null && impl.getName().equals(activeImplName));
            implViews.add(implView);
        }
        implViews.sort(Comparator.comparingInt(ExtImplView::getOrder));
        view.setImplementations(implViews);
        return view;
    }

    private static String defaultInstanceName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
