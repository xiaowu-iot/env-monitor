package io.github.xiaowuiot.envmonitor.alarm;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.xiaowuiot.envmonitor.data.DataType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 超标预警规则：某个因子超过上限或低于下限就预警。适用范围：设备 → 站点 → 全部，留空表示不限。
 */
@Entity
@Table(name = "env_alarm_rule")
public class AlarmRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "alarm_type", length = 16, nullable = false)
    private AlarmType type = AlarmType.OVER_LIMIT;

    @Column(length = 16, nullable = false)
    private String factorCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 16)
    private DataType dataType = DataType.REALTIME;

    private Double upperLimit;

    private Double lowerLimit;

    /** 级别：1 一般、2 较重、3 严重 */
    private int level = 2;

    private Long siteId;

    private Long deviceId;

    private boolean enabled = true;

    @Column(length = 200)
    private String remark;

    public AlarmRule() {
    }

    public AlarmRule(String name, String factorCode, Double upperLimit, Double lowerLimit, int level) {
        this.name = name;
        this.factorCode = factorCode;
        this.upperLimit = upperLimit;
        this.lowerLimit = lowerLimit;
        this.level = level;
    }

    /**
     * 这条规则管不管这台设备
     */
    public boolean covers(Long deviceId, Long siteId) {
        return (this.deviceId == null || this.deviceId.equals(deviceId))
                && (this.siteId == null || this.siteId.equals(siteId));
    }

    /** 阈值的文字描述，比如"≤ 50"、"6 ~ 9" */
    public String thresholdText() {
        if (upperLimit != null && lowerLimit != null) {
            return fmt(lowerLimit) + " ~ " + fmt(upperLimit);
        }
        return upperLimit != null ? "≤ " + fmt(upperLimit) : "≥ " + fmt(lowerLimit);
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AlarmType getType() {
        return type;
    }

    public String getFactorCode() {
        return factorCode;
    }

    public void setFactorCode(String factorCode) {
        this.factorCode = factorCode;
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public Double getUpperLimit() {
        return upperLimit;
    }

    public void setUpperLimit(Double upperLimit) {
        this.upperLimit = upperLimit;
    }

    public Double getLowerLimit() {
        return lowerLimit;
    }

    public void setLowerLimit(Double lowerLimit) {
        this.lowerLimit = lowerLimit;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
