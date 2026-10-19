package io.github.xiaowuiot.envmonitor.agent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.xiaowuiot.envmonitor.common.ApiException;
import io.github.xiaowuiot.envmonitor.user.Permission;
import io.github.xiaowuiot.envmonitor.user.Role;
import io.github.xiaowuiot.envmonitor.user.User;

/**
 * 智能助手（查询）。
 * <ul>
 *     <li>接了大模型（env.ai.provider=openai）：把用户能用的工具交给模型，模型决定调哪个、怎么组织回答</li>
 *     <li>没接大模型（mock）：规则模式，按关键词识别常用问法</li>
 * </ul>
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    /** 没配大模型时的提醒：下载代码直接运行的人一般还没有 key */
    public static final String NO_KEY_HINT = "现在还没有接入 AI 大模型，只能回答上面这些常用问法。想自由对话，在配置文件 config/application.yml 里填上大模型的 key 后重启即可，"
            + "支持 DeepSeek、通义千问、Kimi、智谱等 OpenAI 兼容接口，例如：env.ai.provider=openai、env.ai.base-url=https://api.deepseek.com/v1、"
            + "env.ai.api-key=你的 API Key、env.ai.model=deepseek-chat。";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final int MAX_HISTORY = 16;
    private static final int MAX_ROUNDS = 6;
    private static final int MAX_TOOL_RESULT = 6000;

    public record Reply(String text, List<ToolContext.Chart> charts, List<String> suggestions, String mode) {
    }

    private final AgentTools tools;
    private final RulePlanner planner;
    private final LlmClient llm;
    private final Map<Long, List<Map<String, Object>>> history = new ConcurrentHashMap<>();

    public AgentService(AgentTools tools, RulePlanner planner, LlmClient llm) {
        this.tools = tools;
        this.planner = planner;
        this.llm = llm;
    }

    public String mode() {
        return llm.enabled() ? "llm" : "rule";
    }

    public Reply chat(User user, String message) {
        if (!user.has(Permission.AGENT_USE)) {
            throw ApiException.forbidden("你没有使用智能助手的权限");
        }
        if (message == null || message.isBlank()) {
            throw ApiException.badRequest("请输入问题");
        }
        String msg = message.trim();
        if (msg.length() > 500) {
            throw ApiException.badRequest("问题太长了，请精简到 500 字以内");
        }
        ToolContext ctx = new ToolContext(user);
        String text;
        String mode = mode();
        if (llm.enabled()) {
            try {
                text = llmChat(user, msg, ctx);
            } catch (RuntimeException e) {
                log.warn("大模型调用失败，改用规则模式：{}", e.getMessage());
                text = "（大模型暂时不可用，以下由规则模式回答）\n\n" + ruleChat(user, msg, ctx);
                mode = "rule";
            }
        } else {
            text = ruleChat(user, msg, ctx);
        }
        remember(user.getId(), "user", msg);
        remember(user.getId(), "assistant", text);
        return new Reply(text, ctx.charts(), RulePlanner.EXAMPLES.subList(0, 4), mode);
    }

    private String ruleChat(User user, String msg, ToolContext ctx) {
        RulePlanner.Call call = planner.plan(msg);
        if (call == null) {
            return "这个问题我还没理解。规则模式下可以这样问：\n\n- " + String.join("\n- ", RulePlanner.EXAMPLES)
                    + "\n\n" + NO_KEY_HINT;
        }
        if ("help".equals(call.tool())) {
            return "我可以帮你查运行情况、预警、设备、实时和历史数据、待办工单。比如：\n\n- " + String.join("\n- ", RulePlanner.EXAMPLES);
        }
        AgentTool tool = tools.get(call.tool());
        if (tool.permission() != null && !user.has(tool.permission())) {
            return "你没有「" + tool.permission().label() + "」权限，查不了这个。";
        }
        try {
            return tool.run().apply(call.args(), ctx).text();
        } catch (ApiException e) {
            return e.getMessage();
        }
    }

    private String llmChat(User user, String msg, ToolContext ctx) {
        List<Map<String, Object>> toolDefs = tools.available(user).stream().map(t -> Map.<String, Object>of("type", "function",
                "function", Map.of("name", t.name(), "description", t.description(), "parameters", t.parameters()))).toList();
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt(user)));
        messages.addAll(history.getOrDefault(user.getId(), List.of()));
        messages.add(Map.of("role", "user", "content", msg));
        for (int round = 0; round < MAX_ROUNDS; round++) {
            JsonNode reply = llm.chat(messages, toolDefs);
            JsonNode calls = reply.path("tool_calls");
            if (!calls.isArray() || calls.isEmpty()) {
                String text = reply.path("content").asText("");
                return text.isBlank() ? "（没有生成回答）" : text.trim();
            }
            Map<String, Object> assistant = new LinkedHashMap<>();
            assistant.put("role", "assistant");
            assistant.put("content", reply.path("content").isNull() ? "" : reply.path("content").asText(""));
            assistant.put("tool_calls", JSON.convertValue(calls, List.class));
            messages.add(assistant);
            for (JsonNode call : calls) {
                String result = runTool(user, call.path("function").path("name").asText(),
                        call.path("function").path("arguments").asText("{}"), ctx);
                messages.add(Map.of("role", "tool", "tool_call_id", call.path("id").asText(), "content", result));
            }
        }
        return "这个问题需要的步骤太多了，请换个更具体的问法。";
    }

    private String runTool(User user, String name, String argText, ToolContext ctx) {
        try {
            AgentTool tool = tools.get(name);
            if (tool.permission() != null && !user.has(tool.permission())) {
                return json(Map.of("error", "当前用户没有「" + tool.permission().label() + "」权限"));
            }
            JsonNode args = argText.isBlank() ? JSON.createObjectNode() : JSON.readTree(argText);
            ToolResult r = tool.run().apply(args, ctx);
            String out = json(r.data() == null ? Map.of("text", r.text()) : Map.of("text", r.text(), "data", r.data()));
            return out.length() > MAX_TOOL_RESULT ? out.substring(0, MAX_TOOL_RESULT) + "…（已截断）" : out;
        } catch (ApiException e) {
            return json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("智能助手工具 {} 执行出错", name, e);
            return json(Map.of("error", "工具执行出错：" + e.getMessage()));
        }
    }

    private String systemPrompt(User user) {
        String roles = user.getRoles().stream().map(Role::getName).collect(Collectors.joining("、"));
        return """
                你是环境监测平台的智能助手，帮用户查询平台里的数据。平台按 HJ212-2017 接收在线监测数据，
                有设备、实时和历史数据、超标预警、工单（派单 → 处理 → 审核）等功能。

                当前用户：%s（角色：%s）。现在时间：%s。

                要求：
                1. 数据、预警、工单、设备的情况一律通过工具查询，不要编造数字和名称；工具没查到就如实说。
                2. 你只能查询，不能替用户操作；用户要求派单、审核这类操作时，告诉他到对应页面操作。
                3. 用简体中文，简洁直接，可以用 Markdown 列表；不要输出工具的原始 JSON。
                """.formatted(user.getName(), roles, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
    }

    public void reset(User user) {
        history.remove(user.getId());
    }

    private void remember(Long userId, String role, String content) {
        List<Map<String, Object>> list = history.computeIfAbsent(userId, k -> new ArrayList<>());
        synchronized (list) {
            list.add(Map.of("role", role, "content", content));
            while (list.size() > MAX_HISTORY) {
                list.remove(0);
            }
        }
    }

    private static String json(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
