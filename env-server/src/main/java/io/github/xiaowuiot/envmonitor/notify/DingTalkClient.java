package io.github.xiaowuiot.envmonitor.notify;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 钉钉群机器人。
 * <p>
 * 加签：把 {@code timestamp + "\n" + secret} 用 secret 做 HmacSHA256，结果 Base64 后再 URL 编码，
 * 和 timestamp 一起拼到 webhook 后面。时间戳和钉钉服务器相差超过 1 小时会被拒绝，服务器时间要准。
 */
@Component
public class DingTalkClient {

    public record Result(boolean success, String response) {
    }

    private final RestClient http;

    public DingTalkClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = builder.requestFactory(factory).build();
    }

    public static String sign(long timestamp, String secret) {
        try {
            String toSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] data = mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(data);
        } catch (Exception e) {
            throw new IllegalStateException("计算钉钉签名失败", e);
        }
    }

    public static String signedUrl(String webhook, String secret) {
        if (secret == null || secret.isBlank()) {
            return webhook;
        }
        long ts = System.currentTimeMillis();
        String sign = URLEncoder.encode(sign(ts, secret), StandardCharsets.UTF_8);
        return webhook + (webhook.contains("?") ? "&" : "?") + "timestamp=" + ts + "&sign=" + sign;
    }

    /**
     * 发 Markdown 消息。钉钉要求被 @ 的手机号同时出现在正文里才会高亮，正文由调用方拼好
     */
    public Result sendMarkdown(String webhook, String secret, String title, String text, List<String> atMobiles) {
        Map<String, Object> body = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of("title", title, "text", text),
                "at", Map.of("atMobiles", atMobiles, "isAtAll", false));
        try {
            JsonNode resp = http.post().uri(java.net.URI.create(signedUrl(webhook, secret)))
                    .body(body).retrieve().body(JsonNode.class);
            boolean ok = resp != null && resp.path("errcode").asInt(-1) == 0;
            return new Result(ok, String.valueOf(resp));
        } catch (Exception e) {
            return new Result(false, "发送失败：" + e.getMessage());
        }
    }
}
