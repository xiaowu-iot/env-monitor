package io.github.xiaowuiot.envmonitor.agent;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import io.github.xiaowuiot.envmonitor.config.EnvProperties;

/**
 * OpenAI 兼容的 /chat/completions 接口（带工具调用）。DeepSeek、通义千问、Kimi、智谱、本地 Ollama 都支持这种接口，
 * 换模型只需要改 base-url、api-key 和 model。
 */
@Component
public class LlmClient {

    private final EnvProperties.Ai ai;
    private final RestClient http;

    public LlmClient(EnvProperties props, RestClient.Builder builder) {
        this.ai = props.ai();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(10, ai.timeoutSeconds())));
        RestClient.Builder b = builder.requestFactory(factory);
        if (enabled()) {
            b = b.baseUrl(ai.baseUrl()).defaultHeader("Authorization", "Bearer " + (ai.apiKey() == null ? "" : ai.apiKey()));
        }
        this.http = b.build();
    }

    public boolean enabled() {
        return "openai".equalsIgnoreCase(ai.provider()) && ai.baseUrl() != null && !ai.baseUrl().isBlank()
                && ai.model() != null && !ai.model().isBlank();
    }

    public String model() {
        return ai.model();
    }

    /**
     * @return choices[0].message
     */
    public JsonNode chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = tools.isEmpty()
                ? Map.of("model", ai.model(), "messages", messages, "temperature", 0.2)
                : Map.of("model", ai.model(), "messages", messages, "tools", tools, "tool_choice", "auto", "temperature", 0.2);
        JsonNode resp;
        try {
            resp = http.post().uri("/chat/completions").body(body).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            String text = e.getResponseBodyAsString();
            throw new IllegalStateException("调用大模型失败 HTTP " + e.getStatusCode().value() + "："
                    + (text.length() > 300 ? text.substring(0, 300) : text), e);
        }
        if (resp == null || resp.path("choices").isEmpty()) {
            throw new IllegalStateException("大模型没有返回内容");
        }
        return resp.path("choices").path(0).path("message");
    }
}
