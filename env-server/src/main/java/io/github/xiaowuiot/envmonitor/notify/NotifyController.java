package io.github.xiaowuiot.envmonitor.notify;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.xiaowuiot.envmonitor.common.ApiException;
import io.github.xiaowuiot.envmonitor.common.PageResult;
import io.github.xiaowuiot.envmonitor.config.EnvProperties;
import io.github.xiaowuiot.envmonitor.security.CurrentUser;

/**
 * 站内消息，以及钉钉模拟群收到的消息。
 */
@RestController
@RequestMapping("/api")
public class NotifyController {

    private final InboxRepository inbox;
    private final MockDingTalkController mock;
    private final CurrentUser current;
    private final EnvProperties props;

    public NotifyController(InboxRepository inbox, MockDingTalkController mock, CurrentUser current, EnvProperties props) {
        this.inbox = inbox;
        this.mock = mock;
        this.current = current;
        this.props = props;
    }

    @GetMapping("/notify/status")
    @PreAuthorize("hasAuthority('PERM_SYSTEM_ADMIN')")
    public Map<String, Object> status() {
        String webhook = props.dingtalk().webhook();
        boolean configured = webhook != null && !webhook.isBlank();
        return Map.of("dingtalkConfigured", configured, "mock", configured && webhook.contains("/mock/dingtalk/"),
                "signed", props.dingtalk().secret() != null && !props.dingtalk().secret().isBlank());
    }

    @GetMapping("/notify/mock-messages")
    @PreAuthorize("hasAuthority('PERM_SYSTEM_ADMIN')")
    public List<MockDingTalkController.Received> mockMessages() {
        return mock.messages();
    }

    @DeleteMapping("/notify/mock-messages")
    @PreAuthorize("hasAuthority('PERM_SYSTEM_ADMIN')")
    public Map<String, Boolean> clearMock() {
        mock.clear();
        return Map.of("ok", true);
    }

    @GetMapping("/inbox")
    public PageResult<InboxMessage> inbox(@RequestParam(name = "page", defaultValue = "0") int page,
                                          @RequestParam(name = "size", defaultValue = "20") int size) {
        return PageResult.of(inbox.findByUserIdOrderByIdDesc(current.id(), PageRequest.of(page, Math.min(size, 100))), m -> m);
    }

    @GetMapping("/inbox/unread")
    public Map<String, Long> unread() {
        return Map.of("count", inbox.countByUserIdAndReadFlagFalse(current.id()));
    }

    @PostMapping("/inbox/{id}/read")
    public Map<String, Boolean> read(@PathVariable Long id) {
        InboxMessage m = inbox.findById(id).filter(x -> x.getUserId().equals(current.id()))
                .orElseThrow(() -> ApiException.notFound("消息不存在"));
        m.markRead();
        inbox.save(m);
        return Map.of("ok", true);
    }

    @PostMapping("/inbox/read-all")
    public Map<String, Boolean> readAll() {
        inbox.markAllRead(current.id());
        return Map.of("ok", true);
    }
}
