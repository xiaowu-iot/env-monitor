package io.github.xiaowuiot.envmonitor.common;

/**
 * 操作来源，写进操作记录，方便追溯。
 */
public enum Via {

    WEB("管理后台"),
    SYSTEM("系统自动");

    private final String label;

    Via(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
