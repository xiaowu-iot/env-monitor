package io.github.xiaowuiot.envmonitor.notify;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.xiaowuiot.envmonitor.common.AfterCommit;
import io.github.xiaowuiot.envmonitor.config.EnvProperties;
import io.github.xiaowuiot.envmonitor.user.Permission;
import io.github.xiaowuiot.envmonitor.user.User;
import io.github.xiaowuiot.envmonitor.user.UserRepository;
import jakarta.annotation.PreDestroy;

/**
 * 发通知：站内消息在当前事务里写；钉钉在事务提交之后异步推送。
 * 钉钉群机器人在配置文件里配置（env.dingtalk.webhook / secret），没配就只发站内消息。
 */
@Service
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);
    private static final Pattern VAR = Pattern.compile("\\$\\{(\\w+)}");

    private final InboxRepository inbox;
    private final UserRepository users;
    private final DingTalkClient dingTalk;
    private final EnvProperties props;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "notify");
        t.setDaemon(true);
        return t;
    });

    public NotifyService(InboxRepository inbox, UserRepository users, DingTalkClient dingTalk, EnvProperties props) {
        this.inbox = inbox;
        this.users = users;
        this.dingTalk = dingTalk;
        this.props = props;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /**
     * @param vars    消息里的变量
     * @param inboxTo 收站内消息的人
     * @param atUsers 钉钉群里要 @ 的人（按手机号）
     * @param orderId 关联工单，站内消息点开跳到工单
     */
    @Transactional
    public void publish(NotifyEvent event, Map<String, String> vars, Collection<Long> inboxTo, Collection<User> atUsers,
                        Long orderId) {
        List<String> mobiles = atUsers.stream().map(User::getPhone).filter(Objects::nonNull)
                .filter(p -> !p.isBlank()).distinct().toList();
        Map<String, String> all = new HashMap<>(vars);
        all.put("atText", mobiles.stream().map(m -> "@" + m + " ").reduce("", String::concat));
        String title = render(event.title(), all);
        String text = render(event.content(), all);

        Map<String, String> plain = new HashMap<>(vars);
        plain.put("atText", "");
        String inboxText = render(event.content(), plain).replace("### ", "").trim();
        Set<Long> to = new LinkedHashSet<>(inboxTo);
        to.remove(null);
        for (Long uid : to) {
            inbox.save(new InboxMessage(uid, title, inboxText, orderId == null ? null : "order:" + orderId));
        }

        String webhook = props.dingtalk().webhook();
        if (webhook != null && !webhook.isBlank()) {
            AfterCommit.run(() -> executor.execute(() -> {
                DingTalkClient.Result r = dingTalk.sendMarkdown(webhook, props.dingtalk().secret(), title, text, mobiles);
                if (!r.success()) {
                    log.warn("钉钉推送失败 标题={} 响应={}", title, r.response());
                }
            }));
        }
    }

    public List<Long> userIdsWith(Permission p) {
        return users.findByEnabledTrueOrderById().stream().filter(u -> u.has(p)).map(User::getId).toList();
    }

    static String render(String template, Map<String, String> vars) {
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = vars.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
