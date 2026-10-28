package io.github.xiaowuiot.envmonitor.audit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.xiaowuiot.envmonitor.common.PageResult;
import io.github.xiaowuiot.envmonitor.security.CurrentUser;
import io.github.xiaowuiot.envmonitor.user.Permission;
import io.github.xiaowuiot.envmonitor.user.User;

/**
 * 查登录记录和操作记录。
 * <ul>
 *     <li>登录记录带着 IP、浏览器，只有系统管理员能看</li>
 *     <li>操作记录：系统管理员能看所有人、按用户筛选；其他人只能看自己的，而且不返回 IP</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private static final Map<LoginLog.Action, String> ACTION_LABELS = Map.of(
            LoginLog.Action.LOGIN, "登录成功",
            LoginLog.Action.LOGIN_FAILED, "登录失败",
            LoginLog.Action.LOGOUT, "退出登录");

    private final LoginLogRepository logins;
    private final OperationLogRepository operations;
    private final CurrentUser current;

    public AuditController(LoginLogRepository logins, OperationLogRepository operations, CurrentUser current) {
        this.logins = logins;
        this.operations = operations;
        this.current = current;
    }

    public record LoginRow(Long id, Long userId, String username, String name, LoginLog.Action action, String actionLabel,
                           String message, String ip, String client, String browser, LocalDateTime createdAt) {
        static LoginRow of(LoginLog l) {
            return new LoginRow(l.getId(), l.getUserId(), l.getUsername(), l.getName(), l.getAction(),
                    ACTION_LABELS.get(l.getAction()), l.getMessage(), l.getIp(), l.getClient(), browserOf(l.getUserAgent()),
                    l.getCreatedAt());
        }
    }

    public record OperationRow(Long id, Long userId, String username, String name, String module, String action,
                               Long targetId, String method, String path, int status, boolean success, String message,
                               long durationMs, String ip, String client, LocalDateTime createdAt) {
        static OperationRow of(OperationLog o) {
            return new OperationRow(o.getId(), o.getUserId(), o.getUsername(), o.getName(), o.getModule(), o.getAction(),
                    o.getTargetId(), o.getMethod(), o.getPath(), o.getStatus(), o.isSuccess(), o.getMessage(),
                    o.getDurationMs(), o.getIp(), o.getClient(), o.getCreatedAt());
        }

        /** 给非管理员看的：去掉 IP */
        static OperationRow withoutIp(OperationLog o) {
            OperationRow r = of(o);
            return new OperationRow(r.id, r.userId, r.username, r.name, r.module, r.action, r.targetId, r.method, r.path,
                    r.status, r.success, r.message, r.durationMs, null, r.client, r.createdAt);
        }
    }

    @GetMapping("/logins")
    @PreAuthorize("hasAuthority('PERM_SYSTEM_ADMIN')")
    public PageResult<LoginRow> logins(@RequestParam(name = "userId", required = false) Long userId,
                                       @RequestParam(name = "keyword", required = false) String keyword,
                                       @RequestParam(name = "action", required = false) LoginLog.Action action,
                                       @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(name = "page", defaultValue = "0") int page,
                                       @RequestParam(name = "size", defaultValue = "20") int size) {
        Long uid = scope(userId);
        Specification<LoginLog> spec = (root, q, cb) -> {
            List<Predicate> ps = common(root, cb, uid, uid == null ? keyword : null, from, to);
            if (action != null) {
                ps.add(cb.equal(root.get("action"), action));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return PageResult.of(logins.findAll(spec, pageable(page, size)), LoginRow::of);
    }

    @GetMapping("/operations")
    public PageResult<OperationRow> operations(@RequestParam(name = "userId", required = false) Long userId,
                                               @RequestParam(name = "keyword", required = false) String keyword,
                                               @RequestParam(name = "module", required = false) String module,
                                               @RequestParam(name = "success", required = false) Boolean success,
                                               @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(name = "page", defaultValue = "0") int page,
                                               @RequestParam(name = "size", defaultValue = "20") int size) {
        Long uid = scope(userId);
        Specification<OperationLog> spec = (root, q, cb) -> {
            List<Predicate> ps = common(root, cb, uid, null, from, to);
            if (keyword != null && !keyword.isBlank()) {
                String k = "%" + keyword.strip() + "%";
                ps.add(uid == null
                        ? cb.or(cb.like(root.get("username"), k), cb.like(root.get("name"), k), cb.like(root.get("action"), k))
                        : cb.like(root.get("action"), k));
            }
            if (module != null && !module.isBlank()) {
                ps.add(cb.equal(root.get("module"), module));
            }
            if (success != null) {
                ps.add(cb.equal(root.get("success"), success));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return PageResult.of(operations.findAll(spec, pageable(page, size)),
                isAdmin() ? OperationRow::of : OperationRow::withoutIp);
    }

    @GetMapping("/modules")
    public Set<String> modules() {
        current.get();
        return AuditCatalog.modules();
    }

    /** 不是系统管理员只能查自己 */
    private Long scope(Long userId) {
        User me = current.get();
        return me.permissions().contains(Permission.SYSTEM_ADMIN) ? userId : me.getId();
    }

    private boolean isAdmin() {
        return current.get().permissions().contains(Permission.SYSTEM_ADMIN);
    }

    private static <T> List<Predicate> common(jakarta.persistence.criteria.Root<T> root,
                                              jakarta.persistence.criteria.CriteriaBuilder cb, Long userId, String keyword,
                                              LocalDate from, LocalDate to) {
        List<Predicate> ps = new ArrayList<>();
        if (userId != null) {
            ps.add(cb.equal(root.get("userId"), userId));
        }
        if (keyword != null && !keyword.isBlank()) {
            String k = "%" + keyword.strip() + "%";
            ps.add(cb.or(cb.like(root.get("username"), k), cb.like(root.get("name"), k)));
        }
        if (from != null) {
            ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
        }
        if (to != null) {
            ps.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
        }
        return ps;
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id"));
    }

    /** 浏览器和系统的简称，如 "Chrome · Windows"、"微信 · Android" */
    static String browserOf(String ua) {
        if (ua == null || ua.isBlank()) {
            return null;
        }
        String b = ua.contains("MicroMessenger") ? "微信" : ua.contains("DingTalk") ? "钉钉" : ua.contains("Edg/") ? "Edge"
                : ua.contains("Firefox/") ? "Firefox" : ua.contains("Chrome/") ? "Chrome" : ua.contains("Safari/") ? "Safari"
                : ua.contains("Java-http-client") ? "Java" : "其他";
        String os = ua.contains("Windows") ? "Windows" : ua.contains("Android") ? "Android"
                : ua.contains("iPhone") || ua.contains("iPad") ? "iOS" : ua.contains("Mac OS") ? "macOS"
                : ua.contains("Linux") ? "Linux" : null;
        return os == null ? b : b + " · " + os;
    }
}
