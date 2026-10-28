package io.github.xiaowuiot.envmonitor.user;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 返回给前端的用户信息，不含密码。
 */
public record UserView(Long id, String username, String name, String phone, boolean enabled,
                       List<Long> roleIds, List<String> roleNames, List<String> permissions, LocalDateTime createdAt) {

    public static UserView of(User u) {
        return new UserView(u.getId(), u.getUsername(), u.getName(), u.getPhone(), u.isEnabled(),
                u.getRoles().stream().map(Role::getId).sorted().toList(),
                u.getRoles().stream().map(Role::getName).sorted().toList(),
                u.permissions().stream().map(Enum::name).toList(),
                u.getCreatedAt());
    }
}
