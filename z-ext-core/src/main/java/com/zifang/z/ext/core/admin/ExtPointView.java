package com.zifang.z.ext.core.admin;

import java.util.ArrayList;
import java.util.List;

/**
 * 扩展点的只读视图。
 */
public class ExtPointView {

    /**
     * 控制面用的唯一标识：即扩展点标识本身。
     */
    private String id;
    private String point;
    private String interfaceClass;
    private String type;
    private String description;
    private String version;
    private String activeImpl;
    private List<ExtImplView> implementations = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPoint() {
        return point;
    }

    public void setPoint(String point) {
        this.point = point;
    }

    public String getInterfaceClass() {
        return interfaceClass;
    }

    public void setInterfaceClass(String interfaceClass) {
        this.interfaceClass = interfaceClass;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getActiveImpl() {
        return activeImpl;
    }

    public void setActiveImpl(String activeImpl) {
        this.activeImpl = activeImpl;
    }

    public List<ExtImplView> getImplementations() {
        return implementations;
    }

    public void setImplementations(List<ExtImplView> implementations) {
        this.implementations = implementations;
    }
}
