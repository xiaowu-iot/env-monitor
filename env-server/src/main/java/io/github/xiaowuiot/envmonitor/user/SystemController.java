package io.github.xiaowuiot.envmonitor.user;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.xiaowuiot.envmonitor.common.ApiException;
import io.github.xiaowuiot.envmonitor.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 用户管理。角色是内置的三个，开源版不支持自定义角色。
 */
@RestController
@RequestMapping("/api")
public class SystemController {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final CurrentUser current;

    public SystemController(UserRepository users, RoleRepository roles, PasswordEncoder encoder, CurrentUser current) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.current = current;
    }

    public record UserRequest(
            @NotBlank(message = "请输入用户名") @Pattern(regexp = "[A-Za-z0-9_]{3,20}", message = "用户名为 3–20 位字母、数字或下划线") String username,
            @NotBlank(message = "请输入姓名") String name,
            @Pattern(regexp = "^$|1\\d{10}", message = "手机号格式不正确") String phone,
            String password, Boolean enabled, List<Long> roleIds) {
    }

    /** 选人用的简单列表：派单、站点负责人都要选人 */
    public record UserOption(Long id, String name, List<String> roleNames, List<String> permissions) {
    }

    public record RoleView(Long id, String name, String remark, Set<Permission> permissions) {
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('PERM_SYSTEM_ADMIN')")
    public List<UserView> users() {
        return users.findAll().stream().map(UserView::of).toList();
    }

    @GetMapping("/users/options")
    public List<UserOption> userOptions() {
        current.get();
        return users.findByEnabledTrueOrderById().stream().map(u -> new UserOption(u.getId(), u.getName(),
                u.getRoles().stream().map(Role::getName).toList(), u.permissions().stream().map(Enum::name).toList())).toList();
    }

    @GetMapping("/roles")
    public List<RoleView> roles() {
        current.get();
        return roles.findAll().stream().map(r -> new RoleView(r.getId(), r.getName(), r.getRemark(), r.getPermissions())).toList();
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('PERM_SYSTEM_ADMIN')")
    @Transactional
    public UserView createUser(@Valid @RequestBody UserRequest body) {
        if (users.existsByUsername(body.username())) {
            throw ApiException.badRequest("用户名已存在");
        }
        if (body.password() == null || body.password().length() < 6) {
            throw ApiException.badRequest("密码至少 6 位");
        }
        User u = new User(body.username(), encoder.encode(body.password()), body.name().strip());
        apply(u, body);
        return UserView.of(users.save(u));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('PERM_SYSTEM_ADMIN')")
    @Transactional
    public UserView updateUser(@PathVariable Long id, @Valid @RequestBody UserRequest body) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("用户不存在"));
        if (!u.getUsername().equals(body.username()) && users.existsByUsername(body.username())) {
            throw ApiException.badRequest("用户名已存在");
        }
        if (u.getId().equals(current.id()) && Boolean.FALSE.equals(body.enabled())) {
            throw ApiException.badRequest("不能停用自己");
        }
        u.setUsername(body.username());
        u.setName(body.name().strip());
        if (body.password() != null && !body.password().isBlank()) {
            if (body.password().length() < 6) {
                throw ApiException.badRequest("密码至少 6 位");
            }
            u.setPasswordHash(encoder.encode(body.password()));
        }
        apply(u, body);
        return UserView.of(users.save(u));
    }

    private void apply(User u, UserRequest body) {
        u.setPhone(body.phone() == null || body.phone().isBlank() ? null : body.phone());
        if (body.enabled() != null) {
            u.setEnabled(body.enabled());
        }
        u.setRoles(new HashSet<>(roles.findAllById(body.roleIds() == null ? List.of() : body.roleIds())));
    }
}
