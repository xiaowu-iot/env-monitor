package io.github.xiaowuiot.envmonitor.notify;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 站内消息。
 */
@Entity
@Table(name = "env_inbox", indexes = @Index(name = "idx_inbox_user", columnList = "userId,readFlag"))
public class InboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(length = 200)
    private String title;

    @Column(length = 2000)
    private String content;

    /** 点击跳转，比如 order:12 */
    @Column(length = 100)
    private String link;

    private boolean readFlag;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected InboxMessage() {
    }

    public InboxMessage(Long userId, String title, String content, String link) {
        this.userId = userId;
        this.title = title;
        this.content = content != null && content.length() > 2000 ? content.substring(0, 2000) : content;
        this.link = link;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getLink() {
        return link;
    }

    public boolean isReadFlag() {
        return readFlag;
    }

    public void markRead() {
        this.readFlag = true;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
