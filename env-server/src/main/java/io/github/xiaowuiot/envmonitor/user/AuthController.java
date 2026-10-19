package io.github.xiaowuiot.envmonitor.user;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.xiaowuiot.envmonitor.common.ApiException;
import io.github.xiaowuiot.envmonitor.config.EnvProperties;
import io.github.xiaowuiot.envmonitor.notify.InboxRepository;
import io.github.xiaowuiot.envmonitor.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final SecurityContextRepository contextRepo;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final CurrentUser current;
    private final InboxRepository inbox;
    private final EnvProperties props;

    public AuthController(AuthenticationManager authManager, SecurityContextRepository contextRepo,
                          UserRepository users, PasswordEncoder encoder, CurrentUser current, InboxRepository inbox,
                          EnvProperties props) {
        this.authManager = authManager;
        this.contextRepo = contextRepo;
        this.users = users;
        this.encoder = encoder;
        this.current = current;
        this.inbox = inbox;
        this.props = props;
    }

    public record LoginRequest(@NotBlank(message = "请输入用户名") String username,
                               @NotBlank(message = "请输入密码") String password) {
    }

    public record PasswordRequest(@NotBlank(message = "请输入原密码") String oldPassword,
                                  @NotBlank(message = "请输入新密码") @Size(min = 6, max = 40, message = "新密码长度 6–40 位") String newPassword) {
    }

    public record Me(UserView user, long unread) {
    }

    /**
     * 登录页需要的公开信息：演示模式下列出演示账号，方便体验不同角色
     */
    @GetMapping("/options")
    public Map<String, Object> options() {
        if (!props.init().demoData()) {
            return Map.of("demo", false);
        }
        return Map.of("demo", true, "demoPassword", props.init().demoPassword(), "accounts", List.of(
                Map.of("username", "chen", "label", "陈主管（审核员）"),
                Map.of("username", "zhang", "label", "张工（处理人）"),
                Map.of("username", "liu", "label", "刘工（处理人）")));
    }

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public Me login(@Valid @RequestBody LoginRequest body, HttpServletRequest req, HttpServletResponse resp) {
        Authentication auth;
        try {
            auth = authManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username().strip(), body.password()));
        } catch (DisabledException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "DISABLED", "账号已被停用，请联系系统管理员");
        } catch (AuthenticationException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "用户名或密码错误");
        }
        // 登录成功后换一个会话 ID，防止会话固定攻击
        req.getSession(true);
        req.changeSessionId();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextRepo.saveContext(ctx, req, resp);
        User user = users.findByUsername(body.username().strip()).orElseThrow();
        return new Me(UserView.of(user), inbox.countByUserIdAndReadFlagFalse(user.getId()));
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpServletRequest req) {
        if (req.getSession(false) != null) {
            req.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public Me me() {
        User user = current.get();
        return new Me(UserView.of(user), inbox.countByUserIdAndReadFlagFalse(user.getId()));
    }

    @PostMapping("/password")
    @Transactional
    public Map<String, Boolean> password(@Valid @RequestBody PasswordRequest body) {
        User user = current.get();
        if (!encoder.matches(body.oldPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("原密码不正确");
        }
        user.setPasswordHash(encoder.encode(body.newPassword()));
        users.save(user);
        return Map.of("ok", true);
    }
}
