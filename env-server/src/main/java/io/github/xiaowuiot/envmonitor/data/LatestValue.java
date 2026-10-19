package io.github.xiaowuiot.envmonitor.data;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 每台设备每个因子的最新实时值，实时数据页面和智能助手直接查这张表，不用扫历史数据。
 */
@Entity
@Table(name = "env_latest_value", uniqueConstraints = @UniqueConstraint(columnNames = {"deviceId", "factorCode"}))
public class LatestValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long deviceId;

    @Column(length = 16)
    private String factorCode;

    @Column(name = "val")
    private Double value;

    @Column(length = 4)
    private String flag;

    private LocalDateTime dataTime;

    /** 当前是否处于超标状态（由预警规则判断） */
    private boolean overLimit;

    private LocalDateTime updatedAt;

    protected LatestValue() {
    }

    public LatestValue(Long deviceId, String factorCode) {
        this.deviceId = deviceId;
        this.factorCode = factorCode;
    }

    public Long getId() {
        return id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public String getFactorCode() {
        return factorCode;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public LocalDateTime getDataTime() {
        return dataTime;
    }

    public void setDataTime(LocalDateTime dataTime) {
        this.dataTime = dataTime;
    }

    public boolean isOverLimit() {
        return overLimit;
    }

    public void setOverLimit(boolean overLimit) {
        this.overLimit = overLimit;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
