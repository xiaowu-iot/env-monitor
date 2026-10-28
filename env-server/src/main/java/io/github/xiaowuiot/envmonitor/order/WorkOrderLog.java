package io.github.xiaowuiot.envmonitor.order;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.xiaowuiot.envmonitor.common.Via;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 工单流转记录：谁、什么时候、通过什么途径、做了什么。只增不改，全程留痕。
 */
@Entity
@Table(name = "env_work_order_log", indexes = @Index(name = "idx_log_order", columnList = "orderId"))
public class WorkOrderLog {

    public enum Action {
        CREATE("创建"), SUBMIT("提交处理结果"), APPROVE("审核通过"), REJECT("审核驳回"), NOTIFY("发送通知"),
        RECOVER("数据恢复");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 16)
    private Action action;

    private Long operatorId;

    @Column(length = 32)
    private String operatorName;

    @Column(length = 2000)
    private String content;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 16)
    private Via via;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected WorkOrderLog() {
    }

    public WorkOrderLog(Long orderId, Action action, Long operatorId, String operatorName, String content, Via via) {
        this.orderId = orderId;
        this.action = action;
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.content = content != null && content.length() > 2000 ? content.substring(0, 2000) : content;
        this.via = via;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Action getAction() {
        return action;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public String getContent() {
        return content;
    }

    public Via getVia() {
        return via;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
