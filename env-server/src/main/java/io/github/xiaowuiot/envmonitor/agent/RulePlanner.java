package io.github.xiaowuiot.envmonitor.agent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import io.github.xiaowuiot.envmonitor.device.Device;

/**
 * 规则模式：不接大模型时，用关键词理解常用问法，转成工具调用。更自由的问法、多轮追问要接大模型。
 */
@Component
public class RulePlanner {

    public record Call(String tool, ObjectNode args) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*(?:个)?\\s*(小时|天)");

    static final List<String> EXAMPLES = List.of(
            "今天运行情况怎么样？",
            "现在有哪些未关闭的预警？",
            "哪些设备离线了？",
            "城东污水厂现在的数据",
            "城东污水厂 COD 最近 24 小时的曲线",
            "我的待办工单");

    private final AgentResolver resolve;

    public RulePlanner(AgentResolver resolve) {
        this.resolve = resolve;
    }

    /**
     * @return 识别不了返回 null
     */
    public Call plan(String message) {
        String m = message.trim();
        String place = place(m);
        if (m.matches("^(帮助|你能做什么|你会什么|能干什么|怎么用|你好|您好|hi|hello).*")) {
            return new Call("help", args());
        }
        if (m.matches(".*(曲线|趋势|历史|走势|变化).*") || HOURS.matcher(m).find() && resolve.factor(m) != null) {
            ObjectNode a = args().put("place", place == null ? "" : place).put("hours", hours(m));
            String factor = resolve.factor(m.toLowerCase());
            if (factor != null) {
                a.put("factor", factor);
            }
            return new Call("query_history", a);
        }
        if (m.matches(".*(现在|当前|实时|最新).*") && m.matches(".*(多少|数据|数值|值|浓度|读数|情况).*") && place != null) {
            return new Call("latest_values", args().put("place", place));
        }
        if (m.matches(".*(今天|今日|运行情况|概况|总体|整体|怎么样).*") && !m.matches(".*(预警|报警|超标).*")) {
            return new Call("today_overview", args());
        }
        if (m.matches(".*(待办|工单|任务|待审核|要审核).*")) {
            return new Call("list_orders", args().put("box", m.contains("审核") ? "review" : "todo"));
        }
        if (m.matches(".*(预警|报警|告警|超标|异常).*")) {
            ObjectNode a = args().put("scope", m.matches(".*(待派单|没派|未派|没人处理).*") ? "pending"
                    : m.contains("今天") || m.contains("今日") ? "today" : m.matches(".*(全部|所有|最近).*") ? "all" : "open");
            if (place != null) {
                a.put("place", place);
            }
            return new Call("list_alarms", a);
        }
        if (m.matches(".*(离线|在线|设备|掉线).*")) {
            ObjectNode a = args();
            if (m.contains("离线") || m.contains("掉线")) {
                a.put("online", false);
            } else if (m.contains("在线")) {
                a.put("online", true);
            }
            if (place != null) {
                a.put("place", place);
            }
            return new Call("list_devices", a);
        }
        return null;
    }

    /** 消息里提到的地点（设备名、MN 或站点），没提到返回 null */
    private String place(String m) {
        for (Device d : resolve.devices(null)) {
            if (m.contains(d.getMn()) || m.contains(d.getName())) {
                return d.getMn();
            }
        }
        return resolve.sitesMatching(m).isEmpty() ? null : m;
    }

    private static int hours(String m) {
        Matcher h = HOURS.matcher(m);
        if (h.find()) {
            int n = Integer.parseInt(h.group(1));
            return "天".equals(h.group(2)) ? n * 24 : n;
        }
        return 24;
    }

    private static ObjectNode args() {
        return JSON.createObjectNode();
    }
}
