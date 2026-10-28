package io.github.xiaowuiot.envmonitor.audit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 接口和操作名称的对照：哪些写接口要记操作记录、属于哪个模块、叫什么操作。
 * 路径里的 {id} 记成操作对象 ID。没登记的写接口按"其他操作"记下，不会漏。
 */
public final class AuditCatalog {

    public record Entry(String module, String action) {
    }

    private record Rule(String method, Pattern path, Entry entry) {
    }

    /** 不算操作的写接口：登录退出单独记登录记录；和智能助手聊天、消息已读不改业务数据 */
    private static final List<Pattern> SKIP = List.of(
            Pattern.compile("/api/auth/(login|logout)"),
            Pattern.compile("/api/inbox/.*"),
            Pattern.compile("/api/agent/.*"));

    private static final List<Rule> RULES = new ArrayList<>();

    static {
        String ID = "(\\d+)";
        // 预警处置
        add("POST", "/api/alarms/" + ID + "/dispatch", "预警处置", "预警派单");
        add("POST", "/api/orders/" + ID + "/submit", "预警处置", "提交处理结果");
        add("POST", "/api/orders/" + ID + "/review", "预警处置", "审核工单");
        // 设备运维
        add("POST", "/api/sites", "设备运维", "新增站点");
        add("PUT", "/api/sites/" + ID, "设备运维", "修改站点");
        add("DELETE", "/api/sites/" + ID, "设备运维", "删除站点");
        add("POST", "/api/devices", "设备运维", "新增设备");
        add("PUT", "/api/devices/" + ID, "设备运维", "修改设备");
        add("DELETE", "/api/devices/" + ID, "设备运维", "删除设备");
        add("POST", "/api/factors", "设备运维", "保存监测因子");
        add("POST", "/api/simulator/start", "设备运维", "启动模拟器");
        add("POST", "/api/simulator/stop", "设备运维", "停止模拟器");
        add("POST", "/api/simulator/scenario", "设备运维", "切换模拟场景");
        // 系统配置
        add("POST", "/api/alarm-rules", "系统配置", "新增预警规则");
        add("PUT", "/api/alarm-rules/" + ID, "系统配置", "修改预警规则");
        add("DELETE", "/api/alarm-rules/" + ID, "系统配置", "删除预警规则");
        add("DELETE", "/api/notify/mock-messages", "系统配置", "清空模拟消息");
        add("POST", "/api/users", "系统配置", "新增用户");
        add("PUT", "/api/users/" + ID, "系统配置", "修改用户");
        add("POST", "/api/auth/password", "系统配置", "修改密码");
    }

    private static final Entry OTHER = new Entry("其他", "其他操作");

    private AuditCatalog() {
    }

    private static void add(String method, String regex, String module, String action) {
        RULES.add(new Rule(method, Pattern.compile(regex), new Entry(module, action)));
    }

    /** 要不要记操作记录 */
    public static boolean skip(String path) {
        return SKIP.stream().anyMatch(p -> p.matcher(path).matches());
    }

    public record Match(Entry entry, Long targetId) {
    }

    public static Match match(String method, String path) {
        for (Rule r : RULES) {
            if (!r.method().equals(method)) {
                continue;
            }
            Matcher m = r.path().matcher(path);
            if (m.matches()) {
                Long id = m.groupCount() > 0 && m.group(1).chars().allMatch(Character::isDigit) ? Long.valueOf(m.group(1)) : null;
                return new Match(r.entry(), id);
            }
        }
        return new Match(OTHER, null);
    }

    /** 筛选用的模块列表 */
    public static Set<String> modules() {
        Set<String> s = new LinkedHashSet<>();
        RULES.forEach(r -> s.add(r.entry().module()));
        s.add(OTHER.module());
        return s;
    }
}
