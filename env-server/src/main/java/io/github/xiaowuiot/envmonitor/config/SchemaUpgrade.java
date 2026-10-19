package io.github.xiaowuiot.envmonitor.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 老库升级。ddl-auto=update 只加表加列，不改已有列，枚举加了新值以后老库写不进去：
 * <ul>
 * <li>建表时给枚举列生成的取值检查约束（CHECK ... IN (...)），启动时删掉，取值由程序保证；</li>
 * <li>早期版本把角色权限列建成了数据库枚举类型，改成普通字符串列。</li>
 * </ul>
 */
@Component
@Order(0)
public class SchemaUpgrade implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpgrade.class);

    private final DataSource dataSource;

    public SchemaUpgrade(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase();
            boolean h2 = product.contains("h2");
            if (!h2 && !product.contains("mysql")) {
                return;
            }
            dropEnumChecks(c, h2);
            permissionColumn(c, h2);
        } catch (Exception e) {
            log.warn("检查表结构失败：{}", e.getMessage());
        }
    }

    private void dropEnumChecks(Connection c, boolean h2) throws Exception {
        String query = h2
                ? "SELECT TABLE_NAME, CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                        + "WHERE CONSTRAINT_TYPE = 'CHECK' AND TABLE_SCHEMA = SCHEMA()"
                : "SELECT TABLE_NAME, CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS "
                        + "WHERE CONSTRAINT_TYPE = 'CHECK' AND TABLE_SCHEMA = DATABASE()";
        List<String[]> found = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(query)) {
            while (rs.next()) {
                found.add(new String[] {rs.getString(1), rs.getString(2)});
            }
        }
        for (String[] t : found) {
            String sql = h2 ? "ALTER TABLE \"" + t[0] + "\" DROP CONSTRAINT \"" + t[1] + "\""
                    : "ALTER TABLE `" + t[0] + "` DROP CHECK `" + t[1] + "`";
            try (Statement st = c.createStatement()) {
                st.execute(sql);
            }
        }
        if (!found.isEmpty()) {
            log.info("已去掉 {} 个枚举取值检查约束", found.size());
        }
    }

    private void permissionColumn(Connection c, boolean h2) throws Exception {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getColumns(c.getCatalog(), null, h2 ? "SYS_ROLE_PERMISSION" : "sys_role_permission",
                h2 ? "PERMISSION" : "permission")) {
            if (!rs.next()) {
                return;
            }
            String type = rs.getString("TYPE_NAME");
            if (type == null || !type.toUpperCase().startsWith("ENUM")) {
                return;
            }
        }
        try (Statement st = c.createStatement()) {
            st.execute(h2 ? "ALTER TABLE sys_role_permission ALTER COLUMN permission VARCHAR(32)"
                    : "ALTER TABLE sys_role_permission MODIFY permission VARCHAR(32)");
        }
        log.info("角色权限列已改为字符串类型");
    }
}
