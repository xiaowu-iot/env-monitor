package io.github.xiaowuiot.envmonitor.notify;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.xiaowuiot.envmonitor.config.EnvProperties;

/**
 * 模拟钉钉群机器人。没有真实群机器人时，把通知渠道的 webhook 指到这里，就能完整演示"派单 → 推送"。
 * <p>
 * 和真实接口一样校验加签（时间戳 1 小时内、签名正确），返回值也和真实接口一致，签名算错了这里就能发现。
 * 收到的消息保存在内存里，后台"通知配置 → 模拟群消息"可以看到。
 */
@RestController
public class MockDingTalkController {

    public record Received(LocalDateTime time, boolean signOk, String title, String text, List<String> atMobiles,
                           String error) {
    }

    private static final int MAX = 200;

    private final EnvProperties props;
    private final Deque<Received> messages = new ArrayDeque<>();

    public MockDingTalkController(EnvProperties props) {
        this.props = props;
    }

    @PostMapping("/mock/dingtalk/robot/send")
    public ResponseEntity<Map<String, Object>> send(@RequestParam(name = "timestamp", required = false) Long timestamp,
                                                    @RequestParam(name = "sign", required = false) String sign,
                                                    @RequestBody JsonNode body) {
        if (!props.dingtalk().mockEnabled()) {
            return ResponseEntity.status(404).build();
        }
        String error = verify(timestamp, sign);
        List<String> at = new ArrayList<>();
        body.path("at").path("atMobiles").forEach(n -> at.add(n.asText()));
        String title = body.path("markdown").path("title").asText(body.path("text").path("content").asText(""));
        String text = body.path("markdown").path("text").asText("");
        synchronized (messages) {
            messages.addFirst(new Received(LocalDateTime.now(), error == null, title, text, at, error));
            while (messages.size() > MAX) {
                messages.removeLast();
            }
        }
        if (error != null) {
            return ResponseEntity.ok(Map.of("errcode", 310000, "errmsg", error));
        }
        return ResponseEntity.ok(Map.of("errcode", 0, "errmsg", "ok"));
    }

    private String verify(Long timestamp, String sign) {
        if (timestamp == null || sign == null) {
            return "缺少 timestamp 或 sign";
        }
        if (Math.abs(System.currentTimeMillis() - timestamp) > 3600_000) {
            return "timestamp 和服务器时间相差超过 1 小时";
        }
        String expected = DingTalkClient.sign(timestamp, props.dingtalk().mockSecret());
        return expected.equals(sign) ? null : "sign not match";
    }

    public List<Received> messages() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    public void clear() {
        synchronized (messages) {
            messages.clear();
        }
    }
}
