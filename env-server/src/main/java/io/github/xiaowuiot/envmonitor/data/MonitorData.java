package io.github.xiaowuiot.envmonitor.data;

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
 * 监测数据，一行一个因子（窄表）。因子数量各站点不一样，窄表不用改表结构；
 * 数据量大了以后按月分表或换时序库。
 */
@Entity
@Table(name = "env_monitor_data", indexes = {
        @Index(name = "idx_data_query", columnList = "deviceId,factorCode,dataType,dataTime"),
        @Index(name = "idx_data_time", columnList = "dataTime")})
public class MonitorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long deviceId;

    @Column(length = 32)
    private String mn;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 16)
    private DataType dataType;

    private LocalDateTime dataTime;

    @Column(length = 16)
    private String factorCode;

    /** 实时值 Rtd */
    private Double rtd;

    private Double avgVal;

    private Double minVal;

    private Double maxVal;

    /** 累计值 Cou（流量类因子） */
    private Double cou;

    /** 折算值 ZsRtd / ZsAvg（废气） */
    private Double zsVal;

    /** 数据标记。2017 版（表 8）N 正常、F 停运、M 维护、S 手工输入、D 故障、C 校准、T 超测量上限、B 通讯异常；2025 版（6.7.4）最长 3 位，N、D、C、M、T、Td、St、Stc、Sth、Sd、B 闷炉、F、Sr */
    @Column(length = 4)
    private String flag;

    /** 运行状态 RS */
    private Integer rs;

    /** 运行时间 RT（小时） */
    private Double rt;

    /** 是不是平台主动提取的历史数据 */
    private boolean requested;

    /** 2025 版扩充数据标记 EFlag（仪表自定义，最长 4 位） */
    @Column(length = 8)
    private String eflag;

    /** 2025 版补传数据（RF=1） */
    @Column(columnDefinition = "boolean default false")
    private boolean refill;

    private LocalDateTime receivedAt = LocalDateTime.now();

    protected MonitorData() {
    }

    public MonitorData(Long deviceId, String mn, DataType dataType, LocalDateTime dataTime, String factorCode) {
        this.deviceId = deviceId;
        this.mn = mn;
        this.dataType = dataType;
        this.dataTime = dataTime;
        this.factorCode = factorCode;
    }

    /** 用于判断和画图的主值：实时数据取 Rtd，统计数据取 Avg */
    public Double mainValue() {
        return rtd != null ? rtd : avgVal;
    }

    public Long getId() {
        return id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public String getMn() {
        return mn;
    }

    public DataType getDataType() {
        return dataType;
    }

    public LocalDateTime getDataTime() {
        return dataTime;
    }

    public String getFactorCode() {
        return factorCode;
    }

    public Double getRtd() {
        return rtd;
    }

    public void setRtd(Double rtd) {
        this.rtd = rtd;
    }

    public Double getAvgVal() {
        return avgVal;
    }

    public void setAvgVal(Double avgVal) {
        this.avgVal = avgVal;
    }

    public Double getMinVal() {
        return minVal;
    }

    public void setMinVal(Double minVal) {
        this.minVal = minVal;
    }

    public Double getMaxVal() {
        return maxVal;
    }

    public void setMaxVal(Double maxVal) {
        this.maxVal = maxVal;
    }

    public Double getCou() {
        return cou;
    }

    public void setCou(Double cou) {
        this.cou = cou;
    }

    public Double getZsVal() {
        return zsVal;
    }

    public void setZsVal(Double zsVal) {
        this.zsVal = zsVal;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getEflag() {
        return eflag;
    }

    public void setEflag(String eflag) {
        this.eflag = eflag;
    }

    public boolean isRefill() {
        return refill;
    }

    public void setRefill(boolean refill) {
        this.refill = refill;
    }

    public Integer getRs() {
        return rs;
    }

    public void setRs(Integer rs) {
        this.rs = rs;
    }

    public Double getRt() {
        return rt;
    }

    public void setRt(Double rt) {
        this.rt = rt;
    }

    public boolean isRequested() {
        return requested;
    }

    public void setRequested(boolean requested) {
        this.requested = requested;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }
}
