package io.github.xiaowuiot.envmonitor.ingest;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 原始报文，收发都记。现场联调、扯皮"到底发没发"的时候全靠它。保留天数见 env.hj212.raw-retention-days。
 */
@Entity
@Table(name = "env_raw_message", indexes = {
        @Index(name = "idx_raw_mn", columnList = "mn,createdAt"),
        @Index(name = "idx_raw_time", columnList = "createdAt")})
public class RawMessage {

    public static final String IN = "IN";
    public static final String OUT = "OUT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 32)
    private String mn;

    private Long deviceId;

    @Column(length = 4)
    private String direction;

    @Column(length = 8)
    private String cn;

    @Column(length = 20)
    private String qn;

    @Column(length = 10000)
    private String content;

    private boolean ok = true;

    /** 2025 版加密传输的报文。content 里存的是解密后的明文，方便排查 */
    @jakarta.persistence.Column(columnDefinition = "boolean default false")
    private boolean encrypted;

    @Column(length = 200)
    private String error;

    @Column(length = 64)
    private String remote;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected RawMessage() {
    }

    public RawMessage(String direction, String mn, Long deviceId, String cn, String qn, String content, String remote) {
        this.direction = direction;
        this.mn = mn;
        this.deviceId = deviceId;
        this.cn = cn;
        this.qn = qn;
        this.content = content != null && content.length() > 10000 ? content.substring(0, 10000) : content;
        this.remote = remote;
    }

    public RawMessage encrypted(boolean encrypted) {
        this.encrypted = encrypted;
        return this;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public RawMessage fail(String error) {
        this.ok = false;
        this.error = error != null && error.length() > 200 ? error.substring(0, 200) : error;
        return this;
    }

    public Long getId() {
        return id;
    }

    public String getMn() {
        return mn;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public String getDirection() {
        return direction;
    }

    public String getCn() {
        return cn;
    }

    public String getQn() {
        return qn;
    }

    public String getContent() {
        return content;
    }

    public boolean isOk() {
        return ok;
    }

    public String getError() {
        return error;
    }

    public String getRemote() {
        return remote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
