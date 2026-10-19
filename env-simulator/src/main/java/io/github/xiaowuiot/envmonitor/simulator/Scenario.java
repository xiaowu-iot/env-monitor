package io.github.xiaowuiot.envmonitor.simulator;

/**
 * 模拟场景。切到"超标"就能看到平台从正常 → 预警 → 派单的全过程。
 */
public enum Scenario {

    NORMAL("正常"),
    OVER_LIMIT("超标");

    private final String label;

    Scenario(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
