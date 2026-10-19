package io.github.xiaowuiot.envmonitor.device;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * 监测设备（数采仪）。平台按 MN 号把收到的报文绑定到设备上。
 */
@Entity
@Table(name = "env_device")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 设备唯一标识，2017 版一般 24 位，老设备 14 位 */
    @Column(nullable = false, unique = true, length = 32)
    private String mn;

    @Column(nullable = false, length = 64)
    private String name;

    private Long siteId;

    /** 访问密码 PW，为空时不校验 */
    @Column(length = 32)
    private String password;

    /** 系统编码 ST：32 地表水体环境污染源、31 大气环境污染源、22 空气质量…… */
    @Column(length = 4)
    private String st = "32";

    /** 停用的设备，报文照收但不入库、不预警 */
    private boolean enabled = true;

    private boolean online;

    private LocalDateTime lastSeenAt;

    private LocalDateTime onlineAt;

    private LocalDateTime offlineAt;

    @Column(length = 64)
    private String lastIp;

    @Column(length = 8)
    private String protocolVersion;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "env_device_factor", joinColumns = @JoinColumn(name = "device_id"))
    @OrderColumn(name = "sort")
    @Column(name = "factor_code", length = 16)
    private List<String> factorCodes = new ArrayList<>();

    @Column(length = 200)
    private String remark;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected Device() {
    }

    public Device(String mn, String name, Long siteId) {
        this.mn = mn;
        this.name = name;
        this.siteId = siteId;
    }

    public Long getId() {
        return id;
    }

    public String getMn() {
        return mn;
    }

    public void setMn(String mn) {
        this.mn = mn;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSt() {
        return st;
    }

    public void setSt(String st) {
        this.st = st;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public LocalDateTime getOnlineAt() {
        return onlineAt;
    }

    public void setOnlineAt(LocalDateTime onlineAt) {
        this.onlineAt = onlineAt;
    }

    public LocalDateTime getOfflineAt() {
        return offlineAt;
    }

    public void setOfflineAt(LocalDateTime offlineAt) {
        this.offlineAt = offlineAt;
    }

    public String getLastIp() {
        return lastIp;
    }

    public void setLastIp(String lastIp) {
        this.lastIp = lastIp;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public List<String> getFactorCodes() {
        return factorCodes;
    }

    public void setFactorCodes(List<String> factorCodes) {
        this.factorCodes = new ArrayList<>(new LinkedHashSet<>(factorCodes));
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 给 Set 风格的调用方用 */
    public Set<String> factorSet() {
        return new LinkedHashSet<>(factorCodes);
    }
}
