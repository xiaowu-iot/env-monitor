package io.github.xiaowuiot.envmonitor;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.xiaowuiot.envmonitor.data.LatestValueRepository;
import io.github.xiaowuiot.envmonitor.device.Device;
import io.github.xiaowuiot.envmonitor.device.DeviceRepository;
import io.github.xiaowuiot.envmonitor.device.SecretKeyService;
import io.github.xiaowuiot.envmonitor.simulator.SimDeviceConfig;
import io.github.xiaowuiot.envmonitor.simulator.Simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * HJ212-2025：自带的"热电厂烟气在线监测"按 2025 版加密接入。模拟数采仪用登记的密钥加密上报，平台解开入库；
 * 要求加密的设备发明文不收；心跳包回 9013。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18171",
        "env.hj212.port=19313",
        "env.data-dir=target/test-data-2025",
        "spring.datasource.url=jdbc:h2:mem:v2025;DB_CLOSE_DELAY=-1",
        "env.init.admin-password=admin123"})
class Hj2025FlowTest {

    @Autowired
    DeviceRepository devices;
    @Autowired
    LatestValueRepository latest;
    @Autowired
    SecretKeyService keys;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void encryptedReportHeartbeatAndPlainRejected() {
        Device d = devices.findByMn("88888880000003").orElseThrow();
        assertThat(d.getProtocolVersion()).isEqualTo("2025");
        assertThat(d.isRequireEncryption()).isTrue();
        // 密钥不明文存
        assertThat(d.getSecretKey()).hasSize(32);
        String key = keys.open(d.getSecretKey());

        try (Simulator sim = new Simulator(1)) {
            // 没有密钥、发明文：设备要求加密，不收
            sim.start(SimDeviceConfig.gas(d.getMn()).setHost("127.0.0.1").setPort(19313).setPassword("123456")
                    .setProtocolVersion("2025").setRtdIntervalSeconds(1));
            await().atMost(Duration.ofSeconds(10)).until(() -> count(
                    "SELECT COUNT(*) FROM env_raw_message WHERE device_id = ? AND cn = '2011' AND ok = FALSE AND error LIKE '%明文%'", d.getId()) > 0);
            assertThat(latest.findByDeviceIdAndFactorCode(d.getId(), "a21026")).isEmpty();
            sim.stop(d.getMn());

            // 用登记的密钥加密上报：解开入库，应答是 2025 版（Flag=8），心跳包回 9013
            sim.start(SimDeviceConfig.gas(d.getMn()).setHost("127.0.0.1").setPort(19313).setPassword("123456")
                    .setProtocolVersion("2025").setSecretKey(key).setRtdIntervalSeconds(1).setHeartbeatSeconds(1));
            await().atMost(Duration.ofSeconds(15)).until(() -> latest.findByDeviceIdAndFactorCode(d.getId(), "a21026").isPresent());
            assertThat(count("SELECT COUNT(*) FROM env_raw_message WHERE device_id = ? AND cn = '2011' AND encrypted = TRUE AND ok = TRUE", d.getId()))
                    .isPositive();
            await().atMost(Duration.ofSeconds(10)).until(() -> count(
                    "SELECT COUNT(*) FROM env_raw_message WHERE device_id = ? AND cn = '9014' AND content LIKE '%Flag=8;%'", d.getId()) > 0
                    && count("SELECT COUNT(*) FROM env_raw_message WHERE device_id = ? AND cn = '9013' AND direction = 'OUT'", d.getId()) > 0);
            assertThat(devices.findById(d.getId()).orElseThrow().isOnline()).isTrue();
        }
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
