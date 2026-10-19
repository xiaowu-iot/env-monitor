package io.github.xiaowuiot.envmonitor.agent;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.xiaowuiot.envmonitor.security.CurrentUser;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agent;
    private final CurrentUser current;

    public AgentController(AgentService agent, CurrentUser current) {
        this.agent = agent;
        this.current = current;
    }

    public record ChatRequest(String message) {
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        current.get();
        return Map.of("mode", agent.mode(), "examples", RulePlanner.EXAMPLES);
    }

    @PostMapping("/chat")
    public AgentService.Reply chat(@RequestBody ChatRequest body) {
        return agent.chat(current.get(), body.message());
    }

    @PostMapping("/reset")
    public Map<String, Boolean> reset() {
        agent.reset(current.get());
        return Map.of("ok", true);
    }
}
