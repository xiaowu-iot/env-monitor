package io.github.xiaowuiot.envmonitor.order;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * 预警处理工单。一条预警一张工单：处理人处理 → 提交结果 → 审核人审核（通过关闭 / 驳回重办）。
 */
@Entity
@Table(name = "env_work_order", indexes = {
        @Index(name = "idx_order_handler", columnList = "handlerId,status"),
        @Index(name = "idx_order_reviewer", columnList = "reviewerId,status")})
public class WorkOrder {

    public enum Status {
        PROCESSING("处理中"), SUBMITTED("待审核"), APPROVED("已完成");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, unique = true)
    private String code;

    private Long alarmId;

    @Column(length = 200)
    private String title;

    private Long siteId;

    private Long deviceId;

    private Long handlerId;

    @Column(length = 32)
    private String handlerName;

    private Long reviewerId;

    @Column(length = 32)
    private String reviewerName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 16)
    private Status status = Status.PROCESSING;

    /** 怎么派的单：站点负责人 / 手动派单 */
    @Column(length = 200)
    private String dispatchReason;

    private int rejectCount;

    @Column(length = 2000)
    private String resultText;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime submittedAt;

    private LocalDateTime closedAt;

    protected WorkOrder() {
    }

    public WorkOrder(String code, Long alarmId, String title) {
        this.code = code;
        this.alarmId = alarmId;
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Long getAlarmId() {
        return alarmId;
    }

    public String getTitle() {
        return title;
    }

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public Long getHandlerId() {
        return handlerId;
    }

    public String getHandlerName() {
        return handlerName;
    }

    public void setHandler(Long id, String name) {
        this.handlerId = id;
        this.handlerName = name;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public void setReviewer(Long id, String name) {
        this.reviewerId = id;
        this.reviewerName = name;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getDispatchReason() {
        return dispatchReason;
    }

    public void setDispatchReason(String dispatchReason) {
        this.dispatchReason = dispatchReason;
    }

    public int getRejectCount() {
        return rejectCount;
    }

    public void incrementReject() {
        rejectCount++;
    }

    public String getResultText() {
        return resultText;
    }

    public void setResultText(String resultText) {
        this.resultText = resultText;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }
}
