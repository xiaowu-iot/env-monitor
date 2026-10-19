package io.github.xiaowuiot.envmonitor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.xiaowuiot.envmonitor.agent.AgentService;
import io.github.xiaowuiot.envmonitor.agent.LlmClient;
import io.github.xiaowuiot.envmonitor.agent.AgentTools;
import io.github.xiaowuiot.envmonitor.agent.RulePlanner;
import io.github.xiaowuiot.envmonitor.config.EnvProperties;
import io.github.xiaowuiot.envmonitor.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 智能助手：规则模式的常用问法，以及大模型模式的工具调用循环（用一个假的 OpenAI 兼容接口）。
 */
@SpringBootTest(properties = {
        "env.hj212.enabled=false",
        "env.data-dir=target/test-data-agent",
        "spring.datasource.url=jdbc:h2:mem:agent;DB_CLOSE_DELAY=-1",
        "env.init.admin-password=admin123"})
class AgentTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final List<JsonNode> REQUESTS = new CopyOnWriteArrayList<>();
    static final HttpServer FAKE;

    static {
        try {
            FAKE = HttpServer.create(new InetSocketAddress("127.0.0.1", 18199), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        FAKE.createContext("/v1/chat/completions", ex -> {
            JsonNode req = JSON.readTree(ex.getRequestBody());
            REQUESTS.add(req);
            JsonNode msgs = req.path("messages");
            JsonNode last = msgs.get(msgs.size() - 1);
            Map<String, Object> message = "tool".equals(last.path("role").asText())
                    ? Map.of("role", "assistant", "content", "工具返回：" + last.path("content").asText().substring(0, 20))
                    : Map.of("role", "assistant", "content", "", "tool_calls", List.of(Map.of("id", "c1", "type", "function",
                    "function", Map.of("name", "list_devices", "arguments", "{\"online\":false}"))));
            byte[] body = JSON.writeValueAsBytes(Map.of("choices", List.of(Map.of("message", message))));
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
        FAKE.start();
    }

    @AfterAll
    static void stop() {
        FAKE.stop(0);
    }

    @Autowired
    AgentService agent;
    @Autowired
    AgentTools tools;
    @Autowired
    RulePlanner planner;
    @Autowired
    UserRepository users;
    @Autowired
    org.springframework.web.client.RestClient.Builder restClient;

    @Test
    void ruleMode() {
        var chen = users.findByUsername("chen").orElseThrow();
        assertThat(agent.chat(chen, "今天运行情况怎么样").text()).contains("今日运行概况");
        assertThat(agent.chat(chen, "哪些设备离线了").text()).contains("热电厂烟气在线监测");
        assertThat(agent.chat(chen, "现在有哪些未关闭的预警").text()).contains("没有找到");
        assertThat(agent.chat(chen, "我的待办工单").text()).contains("没有待办");
        assertThat(agent.chat(chen, "城东污水厂现在的数据").text()).contains("城东污水厂在线监测").contains("化学需氧量");
        assertThat(agent.chat(chen, "随便说点什么").text()).contains("还没理解");
    }

    @Test
    void llmModeCallsToolsByPermission() {
        EnvProperties.Ai ai = new EnvProperties.Ai("openai", "http://127.0.0.1:18199/v1", "test", "fake", 30);
        EnvProperties props = new EnvProperties(null, null, ai, null, null, null, "./data");
        AgentService llmAgent = new AgentService(tools, planner, new LlmClient(props, restClient));
        REQUESTS.clear();
        AgentService.Reply r = llmAgent.chat(users.findByUsername("zhang").orElseThrow(), "哪些设备离线了");
        assertThat(r.mode()).isEqualTo("llm");
        assertThat(r.text()).startsWith("工具返回：");
        List<String> names = REQUESTS.get(0).path("tools").findValuesAsText("name");
        assertThat(names).contains("list_devices", "list_orders");
        assertThat(REQUESTS.get(1).toString()).contains("\"role\":\"tool\"").contains("热电厂");
    }
}
