package io.github.xiaowuiot.envmonitor.alarm;

/**
 * 预警类型。开源版只做超标预警。
 */
public enum AlarmType {

    OVER_LIMIT("超标");

    private final String label;

    AlarmType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
