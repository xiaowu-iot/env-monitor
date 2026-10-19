package io.github.xiaowuiot.envmonitor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.xiaowuiot.envmonitor.config.EnvProperties;
import io.github.xiaowuiot.envmonitor.device.DeviceRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 演示数据保护：自带数据普通账号不能改删；敏感操作不开放；管理员不受限制；开启时备份数据库。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18076",
        "env.hj212.port=19218",
        "env.data-dir=./target/test-data-demo/${random.uuid}",
        "env.init.admin-password=admin123",
        "env.dingtalk.mock-base-url=http://127.0.0.1:18076",
        "env.demo.protect=true"})
class DemoGuardTest {

    static final int PORT = 18076;

    @Autowired
    DeviceRepository devices;
    @Autowired
    EnvProperties props;

    @Test
    void seedDataIsProtected() throws Exception {
        long deviceId = devices.findByMn("88888880000001").orElseThrow().getId();

        Api chen = Api.login(PORT, "chen", "demo123");
        Api.Resp r = chen.delete("/api/devices/" + deviceId);
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.body().path("code").asText()).isEqualTo("DEMO_PROTECTED");
        assertThat(devices.findById(deviceId)).isPresent();

        // 敏感操作不开放，查询照常
        Api zhang = Api.login(PORT, "zhang", "demo123");
        assertThat(zhang.post("/api/auth/password", Map.of("oldPassword", "demo123", "newPassword", "hacked1")).status())
                .isEqualTo(403);
        assertThat(chen.post("/api/simulator/stop", "{}").status()).isEqualTo(403);
        assertThat(chen.get("/api/devices").status()).isEqualTo(200);

        // 管理员不受限制
        Api admin = Api.login(PORT, "admin", "admin123");
        assertThat(admin.post("/api/auth/password", Map.of("oldPassword", "admin123", "newPassword", "admin456")).status())
                .isEqualTo(200);

        // 第一次开启时备份了数据库
        try (Stream<Path> files = Files.list(Path.of(props.dataDir(), "backup"))) {
            assertThat(files.filter(f -> f.toString().endsWith(".zip"))).hasSize(1);
        }
    }
}
