package io.github.xiaowuiot.envmonitor.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.xiaowuiot.envmonitor.config.EnvProperties;

/**
 * 演示数据保护：第一次开启 env.demo.protect 时，记下每张表当时的最大 ID，ID 不超过它的就是自带的演示数据，
 * 普通账号不能改也不能删；之后新建的数据不受限制。基线存在 env_demo_baseline 表里，重启不会变。
 */
@Component
@Order(2)
public class DemoProtection implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoProtection.class);

    /** 接口路径 → 表名 */
    static final Map<String, String> RESOURCES = Map.ofEntries(
            Map.entry("sites", "env_site"),
            Map.entry("devices", "env_device"),
            Map.entry("alarm-rules", "env_alarm_rule"),
            Map.entry("users", "sys_user"));

    private final JdbcTemplate jdbc;
    private final EnvProperties props;
    private final Map<String, Long> baseline = new ConcurrentHashMap<>();

    public DemoProtection(JdbcTemplate jdbc, EnvProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public boolean enabled() {
        return props.demo().protect();
    }

    public boolean exempt(String username) {
        return props.demo().admins().contains(username);
    }

    /** 这条数据是开启保护时就有的 */
    public boolean isProtected(String resource, long id) {
        Long max = baseline.get(resource);
        return max != null && id <= max;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled()) {
            return;
        }
        jdbc.execute("CREATE TABLE IF NOT EXISTS env_demo_baseline (resource VARCHAR(64) PRIMARY KEY, max_id BIGINT NOT NULL)");
        jdbc.query("SELECT resource, max_id FROM env_demo_baseline", rs -> {
            baseline.put(rs.getString(1), rs.getLong(2));
        });
        boolean first = baseline.isEmpty();
        RESOURCES.forEach((resource, table) -> {
            if (baseline.containsKey(resource)) {
                return;
            }
            Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
            jdbc.update("INSERT INTO env_demo_baseline (resource, max_id) VALUES (?, ?)", resource, max);
            baseline.put(resource, max);
        });
        log.info("演示数据保护已开启：开启时已有的数据不能被普通账号修改和删除");
        if (first) {
            backup();
        }
    }

    /** 每天凌晨备份一次数据库，保留最近 env.demo.backup-keep 份 */
    @Scheduled(cron = "0 17 3 * * *")
    public void scheduledBackup() {
        if (enabled()) {
            backup();
        }
    }

    void backup() {
        int keep = props.demo().backupKeep();
        String url = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getURL());
        if (keep <= 0 || url == null || !url.startsWith("jdbc:h2:file:")) {
            return;
        }
        try {
            Path dir = Path.of(props.dataDir(), "backup").toAbsolutePath();
            Files.createDirectories(dir);
            Path file = dir.resolve("db-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip");
            jdbc.execute("BACKUP TO '" + file.toString().replace("'", "''") + "'");
            log.info("数据库已备份到 {}", file);
            List<Path> old;
            try (Stream<Path> files = Files.list(dir)) {
                old = files.filter(f -> f.getFileName().toString().matches("db-\\d{8}-\\d{6}\\.zip"))
                        .sorted(Comparator.reverseOrder()).skip(keep).toList();
            }
            for (Path f : old) {
                Files.deleteIfExists(f);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("数据库备份失败：{}", e.getMessage());
        }
    }
}
