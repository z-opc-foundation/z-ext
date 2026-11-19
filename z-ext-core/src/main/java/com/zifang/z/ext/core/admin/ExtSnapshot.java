package com.zifang.z.ext.core.admin;

import java.util.ArrayList;
import java.util.List;

/**
 * 某个进程的扩展点全量快照。
 * <p>
 * 控制面读取一个进程的全部扩展点/实现状态时拿这个对象。多进程部署时，
 * 每个进程各持一份快照，控制面聚合后展示；切换指令则按进程逐个下发。
 */
public class ExtSnapshot {

    private String instance;
    private long timestamp;
    private List<ExtPointView> points = new ArrayList<>();

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public List<ExtPointView> getPoints() {
        return points;
    }

    public void setPoints(List<ExtPointView> points) {
        this.points = points;
    }
}
