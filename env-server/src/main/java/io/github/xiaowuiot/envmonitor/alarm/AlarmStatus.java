package io.github.xiaowuiot.envmonitor.alarm;

/**
 * 预警的处理状态，跟着工单走：派单 → 处理中，处理人提交 → 待审核，审核通过 → 已关闭，驳回 → 回到处理中。
 */
public enum AlarmStatus {

    PENDING("待派单"),
    PROCESSING("处理中"),
    REVIEWING("待审核"),
    CLOSED("已关闭");

    private final String label;

    AlarmStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
