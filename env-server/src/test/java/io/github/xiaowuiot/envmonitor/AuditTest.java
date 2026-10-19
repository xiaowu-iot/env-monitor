package io.github.xiaowuiot.envmonitor;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录记录和操作记录：登录成功失败、退出都记；写操作成功失败都记；只有系统管理员能看所有人的记录。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18077",
        "env.hj212.port=19219",
        "env.data-dir=target/test-data-audit",
        "spring.datasource.url=jdbc:h2:mem:audit;DB_CLOSE_DELAY=-1",
        "env.init.admin-password=admin123",
        "env.dingtalk.mock-base-url=http://127.0.0.1:18077"})
class AuditTest {

    static final int PORT = 18077;

    @Test
    void loginAndOperationLogs() {
        assertThat(new Api(PORT).post("/api/auth/login", Map.of("username", "chen", "password", "wrong")).status()).isEqualTo(401);
        Api chen = Api.login(PORT, "chen", "demo123");
        assertThat(chen.post("/api/auth/password", Map.of("oldPassword", "demo123", "newPassword", "demo123")).status()).isEqualTo(200);
        Api zhang = Api.login(PORT, "zhang", "demo123");
        assertThat(zhang.post("/api/alarm-rules", Map.of("name", "x")).status()).isGreaterThanOrEqualTo(400);
        zhang.post("/api/auth/logout", "{}");

        Api admin = Api.login(PORT, "admin", "admin123");
        JsonNode logins = admin.get("/api/audit/logins?keyword=chen").body().path("items");
        assertThat(logins.findValuesAsText("actionLabel")).contains("登录成功", "登录失败");
        assertThat(admin.get("/api/audit/logins?keyword=zhang&action=LOGOUT").body().path("total").asInt()).isEqualTo(1);

        JsonNode ops = admin.get("/api/audit/operations?keyword=chen").body().path("items");
        assertThat(ops.get(0).path("action").asText()).isEqualTo("修改密码");
        assertThat(ops.get(0).path("success").asBoolean()).isTrue();
        assertThat(ops.get(0).path("name").asText()).isEqualTo("陈主管");

        JsonNode denied = admin.get("/api/audit/operations?keyword=zhang&success=false").body().path("items");
        assertThat(denied.get(0).path("action").asText()).isEqualTo("新增预警规则");
        assertThat(denied.get(0).path("status").asInt()).isGreaterThanOrEqualTo(400);
        assertThat(denied.get(0).path("message").asText()).isNotBlank();

        // 非管理员只能看到自己的，传别人的 userId 也没用
        long adminId = admin.get("/api/auth/me").body().path("user").path("id").asLong();
        JsonNode mine = chen.get("/api/audit/operations?userId=" + adminId).body().path("items");
        assertThat(mine.findValuesAsText("username")).containsOnly("chen");
    }
}
