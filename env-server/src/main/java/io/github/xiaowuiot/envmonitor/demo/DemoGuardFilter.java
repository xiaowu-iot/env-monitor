package io.github.xiaowuiot.envmonitor.demo;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.xiaowuiot.envmonitor.security.AuthUser;

/**
 * 公开演示站的写操作限制，规则见 {@link io.github.xiaowuiot.envmonitor.config.EnvProperties.Demo}。
 * 查询不受影响；预警处置、派单、派车、虚拟设备这些体验流程照常可用。
 */
public class DemoGuardFilter extends OncePerRequestFilter {

    /** 不开放的写操作 */
    private static final List<Pattern> CLOSED = List.of(
            Pattern.compile("/api/auth/password"),
            Pattern.compile("/api/users(/.*)?"),
            Pattern.compile("/api/notify/mock-messages"),
            Pattern.compile("/api/simulator(/.*)?"));

    private static final Pattern ITEM = Pattern.compile(
            "/api/(" + String.join("|", DemoProtection.RESOURCES.keySet()) + ")/(\\d+)");

    private final DemoProtection protection;

    public DemoGuardFilter(DemoProtection protection) {
        this.protection = protection;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        return !protection.enabled() || !path(request).startsWith("/api/")
                || "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user) || protection.exempt(user.getUsername())) {
            chain.doFilter(req, resp);
            return;
        }
        String path = path(req);
        if (CLOSED.stream().anyMatch(p -> p.matcher(path).matches())) {
            deny(resp, "演示环境不开放这个操作");
            return;
        }
        Matcher m = ITEM.matcher(path);
        if (m.matches() && ("PUT".equals(req.getMethod()) || "DELETE".equals(req.getMethod()))
                && protection.isProtected(m.group(1), Long.parseLong(m.group(2)))) {
            deny(resp, "演示环境：系统自带的演示数据不能修改或删除，可以自己新建一条再试");
            return;
        }
        chain.doFilter(req, resp);
    }

    /** 去掉部署时的上下文路径（如 /env-monitor） */
    private static String path(HttpServletRequest req) {
        return req.getRequestURI().substring(req.getContextPath().length());
    }

    private static void deny(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpStatus.FORBIDDEN.value());
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write("{\"code\":\"DEMO_PROTECTED\",\"message\":\"" + message + "\"}");
    }
}
