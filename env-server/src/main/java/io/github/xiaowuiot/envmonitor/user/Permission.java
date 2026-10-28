package io.github.xiaowuiot.envmonitor.user;

/**
 * 权限点。开源版内置三个角色（系统管理员、审核员、处理人），见 DataInitializer。
 */
public enum Permission {

    DEVICE_VIEW("查看设备和数据"),
    DEVICE_MANAGE("管理设备"),
    ALARM_VIEW("查看预警"),
    RULE_MANAGE("配置预警规则"),
    ORDER_DISPATCH("派单"),
    ORDER_HANDLE("处理工单"),
    ORDER_REVIEW("审核工单"),
    SIMULATOR("设备模拟器"),
    AGENT_USE("使用智能助手"),
    SYSTEM_ADMIN("系统管理");

    private final String label;

    Permission(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Spring Security 里的权限名 */
    public String authority() {
        return "PERM_" + name();
    }
}
