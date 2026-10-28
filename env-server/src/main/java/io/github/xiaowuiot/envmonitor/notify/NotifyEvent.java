package io.github.xiaowuiot.envmonitor.notify;

/**
 * 会发通知的业务事件，以及消息内容（钉钉用 Markdown，${变量} 会被替换）。
 */
public enum NotifyEvent {

    ORDER_CREATED(
            "预警派单：${siteName} ${factorName}${typeLabel}",
            "### 预警派单\n- 工单：${orderCode}\n- 站点：${siteName}\n- 设备：${deviceName}（${mn}）\n- 预警：${content}\n"
                    + "- 级别：${levelText}\n- 处理人：${handlerName}\n- 审核人：${reviewerName}\n\n${atText}请及时处理"),
    ORDER_SUBMITTED(
            "待审核：${orderCode} ${siteName}",
            "### 工单待审核\n- 工单：${orderCode}\n- 站点：${siteName}\n- 预警：${content}\n- 处理人：${handlerName}\n"
                    + "- 处理结果：${resultText}\n\n${atText}请审核"),
    ORDER_REVIEWED(
            "审核${reviewResult}：${orderCode}",
            "### 工单审核${reviewResult}\n- 工单：${orderCode}\n- 站点：${siteName}\n- 审核人：${reviewerName}\n"
                    + "- 审核意见：${reviewComment}\n\n${atText}"),
    ALARM_UNASSIGNED(
            "预警待派单：${siteName} ${factorName}${typeLabel}",
            "### 预警待人工派单\n- 站点：${siteName}\n- 设备：${deviceName}（${mn}）\n- 预警：${content}\n- 级别：${levelText}\n\n"
                    + "站点没有配置负责人，请到平台手动派单");

    private final String title;
    private final String content;

    NotifyEvent(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public String title() {
        return title;
    }

    public String content() {
        return content;
    }
}
