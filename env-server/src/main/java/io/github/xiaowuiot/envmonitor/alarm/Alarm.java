package io.github.xiaowuiot.envmonitor.alarm;

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
 * 一条预警。同一条规则、同一台设备、同一个因子，没关闭之前只保留一条，后续再超标只累加次数、更新最新值。
 * 设备、站点名称存快照，改名之后历史预警仍然看得懂。
 */
@Entity
@Table(name = "env_alarm", indexes = {
        @Index(name = "idx_alarm_open", columnList = "ruleId,deviceId,factorCode,status"),
        @Index(name = "idx_alarm_time", columnList = "firstAt")})
public class Alarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ruleId;

    @Column(length = 64)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "alarm_type", length = 16)
    private AlarmType type;

    private int level;

    private Long deviceId;

    @Column(length = 32)
    private String mn;

    @Column(length = 64)
    private String deviceName;

    private Long siteId;

    @Column(length = 64)
    private String siteName;

    @Column(length = 16)
    private String factorCode;

    @Column(length = 32)
    private String factorName;

    @Column(name = "val")
    private Double value;

    private Double peakValue;

    @Column(length = 64)
    private String threshold;

    @Column(length = 500)
    private String content;

    private LocalDateTime firstAt;

    private LocalDateTime lastAt;

    private int hitCount = 1;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 16)
    private AlarmStatus status = AlarmStatus.PENDING;

    /** 数据已经恢复正常（但预警还要走完处理流程才关闭） */
    private boolean recovered;

    private LocalDateTime recoveredAt;

    private Long orderId;

    private LocalDateTime closedAt;

    protected Alarm() {
    }

    public Alarm(AlarmRule rule, Long deviceId, String mn, String deviceName) {
        this.ruleId = rule.getId();
        this.ruleName = rule.getName();
        this.type = rule.getType();
        this.level = rule.getLevel();
        this.threshold = rule.thresholdText();
        this.deviceId = deviceId;
        this.mn = mn;
        this.deviceName = deviceName;
        this.firstAt = LocalDateTime.now();
        this.lastAt = firstAt;
    }

    public String levelText() {
        return switch (level) {
            case 3 -> "严重";
            case 2 -> "较重";
            default -> "一般";
        };
    }

    public void hit(Double value, String content) {
        this.value = value;
        if (value != null && (peakValue == null || Math.abs(value) > Math.abs(peakValue))) {
            this.peakValue = value;
        }
        if (content != null) {
            this.content = content.length() > 500 ? content.substring(0, 500) : content;
        }
        this.lastAt = LocalDateTime.now();
        this.recovered = false;
        this.recoveredAt = null;
    }

    public Long getId() {
        return id;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public AlarmType getType() {
        return type;
    }

    public int getLevel() {
        return level;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public String getMn() {
        return mn;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getFactorCode() {
        return factorCode;
    }

    public void setFactorCode(String factorCode) {
        this.factorCode = factorCode;
    }

    public String getFactorName() {
        return factorName;
    }

    public void setFactorName(String factorName) {
        this.factorName = factorName;
    }

    public Double getValue() {
        return value;
    }

    public Double getPeakValue() {
        return peakValue;
    }

    public String getThreshold() {
        return threshold;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getFirstAt() {
        return firstAt;
    }

    public LocalDateTime getLastAt() {
        return lastAt;
    }

    public int getHitCount() {
        return hitCount;
    }

    public void incrementHit() {
        hitCount++;
    }

    public AlarmStatus getStatus() {
        return status;
    }

    public void setStatus(AlarmStatus status) {
        this.status = status;
        if (status == AlarmStatus.CLOSED) {
            closedAt = LocalDateTime.now();
        }
    }

    public boolean isRecovered() {
        return recovered;
    }

    public void markRecovered() {
        this.recovered = true;
        this.recoveredAt = LocalDateTime.now();
    }

    public LocalDateTime getRecoveredAt() {
        return recoveredAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }
}
