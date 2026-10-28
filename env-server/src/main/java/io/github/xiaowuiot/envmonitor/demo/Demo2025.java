package io.github.xiaowuiot.envmonitor.demo;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.github.xiaowuiot.envmonitor.device.Device;
import io.github.xiaowuiot.envmonitor.device.DeviceRepository;
import io.github.xiaowuiot.envmonitor.device.SecretKeyService;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Versions;

/**
 * 自带演示设备里的"热电厂烟气在线监测"按 HJ212-2025 接入：登记一个随机的初始密钥、要求加密传输，
 * 内置模拟器按这台设备的密钥加密上报，页面上能看到加密报文和心跳包。
 * <p>
 * 新装的库和已经在跑的演示站都只做一次（env_demo_marker 里记一笔）；设备被改过版本或已经有密钥的不动。
 */
@Component
@Order(4)
public class Demo2025 implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Demo2025.class);
    static final String MN = "88888880000003";
    static final String MARK = "v2025-v1";

    private final JdbcTemplate jdbc;
    private final DeviceRepository devices;
    private final SecretKeyService keys;

    public Demo2025(JdbcTemplate jdbc, DeviceRepository devices, SecretKeyService keys) {
        this.jdbc = jdbc;
        this.devices = devices;
        this.keys = keys;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS env_demo_marker (name VARCHAR(64) PRIMARY KEY, created_at TIMESTAMP)");
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM env_demo_marker WHERE name = ?", Integer.class, MARK);
        if (n != null && n > 0) {
            return;
        }
        Device d = devices.findByMn(MN).orElse(null);
        if (d != null && d.getSecretKey() == null && !Hj212Versions.V2005.equals(d.getProtocolVersion())) {
            d.setProtocolVersion(Hj212Versions.V2025);
            d.setRequireEncryption(true);
            devices.save(d);
            keys.register(d, null);
            log.info("演示设备 {} 改为按 HJ212-2025 加密接入", MN);
        }
        jdbc.update("INSERT INTO env_demo_marker (name, created_at) VALUES (?, ?)", MARK, Timestamp.valueOf(LocalDateTime.now()));
    }
}
