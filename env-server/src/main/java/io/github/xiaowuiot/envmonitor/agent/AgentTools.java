package io.github.xiaowuiot.envmonitor.agent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;

import io.github.xiaowuiot.envmonitor.alarm.Alarm;
import io.github.xiaowuiot.envmonitor.alarm.AlarmRepository;
import io.github.xiaowuiot.envmonitor.alarm.AlarmStatus;
import io.github.xiaowuiot.envmonitor.common.ApiException;
import io.github.xiaowuiot.envmonitor.data.DataQueryService;
import io.github.xiaowuiot.envmonitor.data.DataType;
import io.github.xiaowuiot.envmonitor.device.Device;
import io.github.xiaowuiot.envmonitor.device.FactorService;
import io.github.xiaowuiot.envmonitor.order.WorkOrder;
import io.github.xiaowuiot.envmonitor.order.WorkOrderRepository;
import io.github.xiaowuiot.envmonitor.order.WorkOrderService;
import io.github.xiaowuiot.envmonitor.report.ReportService;
import io.github.xiaowuiot.envmonitor.user.Permission;
import io.github.xiaowuiot.envmonitor.user.User;

import static io.github.xiaowuiot.envmonitor.agent.AgentResolver.blank;

/**
 * 智能助手的查询工具：运行概况、预警、设备、实时数据、历史数据、工单。
 */
@Component
public class AgentTools {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final AgentResolver resolve;
    private final AlarmRepository alarms;
    private final WorkOrderRepository orders;
    private final WorkOrderService orderService;
    private final DataQueryService dataQuery;
    private final ReportService reports;
    private final FactorService factors;
    private final List<AgentTool> tools = new ArrayList<>();

    public AgentTools(AgentResolver resolve, AlarmRepository alarms, WorkOrderRepository orders,
                      WorkOrderService orderService, DataQueryService dataQuery, ReportService reports,
                      FactorService factors) {
        this.resolve = resolve;
        this.alarms = alarms;
        this.orders = orders;
        this.orderService = orderService;
        this.dataQuery = dataQuery;
        this.reports = reports;
        this.factors = factors;
        register();
    }

    public List<AgentTool> available(User u) {
        return tools.stream().filter(t -> t.permission() == null || u.has(t.permission())).toList();
    }

    public AgentTool get(String name) {
        return tools.stream().filter(t -> t.name().equals(name)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("没有这个工具：" + name));
    }

    private void register() {
        tools.add(new AgentTool("today_overview", "查询今天的运行概况：设备在线情况、预警数量、工单办结情况。",
                schema(Map.of()), null, this::overview));
        tools.add(new AgentTool("list_alarms",
                "查询预警。可按范围（未关闭、待派单、今天、最近 7 天）、地点（站点、设备名或 MN）筛选。",
                schema(Map.of("scope", prop("string", "open 未关闭（默认）、pending 待派单、today 今天产生的、all 最近 7 天全部"),
                        "place", prop("string", "站点、设备名称或 MN，可不填"))),
                Permission.ALARM_VIEW, this::listAlarms));
        tools.add(new AgentTool("list_devices", "查询设备：在线 / 离线、最后上报时间。",
                schema(Map.of("online", prop("boolean", "true 只看在线，false 只看离线，不填看全部"),
                        "place", prop("string", "站点、设备名称或 MN，可不填"))),
                Permission.DEVICE_VIEW, this::listDevices));
        tools.add(new AgentTool("latest_values", "查询设备当前的实时监测值（各因子最新数据、是否超标）。",
                schema(Map.of("place", prop("string", "站点、设备名称或 MN，不填看全部"))),
                Permission.DEVICE_VIEW, this::latestValues));
        tools.add(new AgentTool("query_history", "查询某台设备某个因子一段时间的历史数据，返回统计值并在页面上画曲线。",
                schema(Map.of("place", prop("string", "站点、设备名称或 MN"),
                        "factor", prop("string", "因子名称或编码，比如 COD、氨氮、w01018"),
                        "hours", prop("integer", "最近多少小时，默认 24")), "place"),
                Permission.DEVICE_VIEW, this::queryHistory));
        tools.add(new AgentTool("list_orders", "查询工单：我的待办、待我审核。",
                schema(Map.of("box", prop("string", "todo 我的待办（默认）、review 待我审核"))),
                null, this::listOrders));
    }

    private ToolResult overview(JsonNode args, ToolContext ctx) {
        ReportService.Summary s = reports.today();
        return new ToolResult(reports.markdown(s), s);
    }

    private ToolResult listAlarms(JsonNode args, ToolContext ctx) {
        String scope = text(args, "scope");
        String place = text(args, "place");
        List<Alarm> base = switch (blank(scope) ? "open" : scope) {
            case "pending" -> alarms.findByStatus(AlarmStatus.PENDING);
            case "today" -> alarms.findByFirstAtBetween(LocalDate.now().atStartOfDay(), LocalDateTime.now());
            case "all" -> alarms.findByFirstAtBetween(LocalDate.now().minusDays(6).atStartOfDay(), LocalDateTime.now());
            default -> alarms.findByStatusNot(AlarmStatus.CLOSED);
        };
        Set<Long> siteIds = resolve.sitesMatching(place);
        List<Long> deviceIds = blank(place) ? null : resolve.devices(place).stream().map(Device::getId).toList();
        List<Alarm> list = base.stream()
                .filter(a -> blank(place) || (a.getSiteId() != null && siteIds.contains(a.getSiteId()))
                        || (deviceIds != null && deviceIds.contains(a.getDeviceId())))
                .sorted(Comparator.comparing(Alarm::getId).reversed()).toList();
        if (list.isEmpty()) {
            return new ToolResult("没有找到符合条件的预警。", List.of());
        }
        StringBuilder sb = new StringBuilder("共 " + list.size() + " 条预警" + (list.size() > 10 ? "，最近 10 条：" : "：") + "\n\n");
        List<Map<String, Object>> data = new ArrayList<>();
        for (Alarm a : list.stream().limit(10).toList()) {
            WorkOrder o = a.getOrderId() == null ? null : orders.findById(a.getOrderId()).orElse(null);
            sb.append("- **#").append(a.getId()).append("** ").append(a.getSiteName()).append("：").append(a.getContent())
                    .append("（").append(a.levelText()).append("，").append(a.getStatus().label())
                    .append(o == null ? "" : "，处理人 " + o.getHandlerName())
                    .append(a.isRecovered() ? "，数据已恢复" : "").append("，").append(a.getFirstAt().format(TIME)).append("）\n");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("site", a.getSiteName());
            m.put("content", a.getContent());
            m.put("level", a.levelText());
            m.put("status", a.getStatus().label());
            m.put("handler", o == null ? null : o.getHandlerName());
            m.put("firstAt", a.getFirstAt().toString());
            data.add(m);
        }
        return new ToolResult(sb.toString(), data);
    }

    private ToolResult listDevices(JsonNode args, ToolContext ctx) {
        List<Device> list = resolve.devices(text(args, "place"));
        JsonNode online = args.path("online");
        if (online.isBoolean()) {
            list = list.stream().filter(d -> d.isOnline() == online.asBoolean()).toList();
        }
        if (list.isEmpty()) {
            return new ToolResult("没有符合条件的设备。", List.of());
        }
        StringBuilder sb = new StringBuilder("共 " + list.size() + " 台设备：\n\n");
        List<Map<String, Object>> data = new ArrayList<>();
        for (Device d : list) {
            sb.append("- ").append(d.getName()).append("（").append(d.getMn()).append("）：")
                    .append(d.isOnline() ? "在线" : "离线").append("，最后上报 ")
                    .append(d.getLastSeenAt() == null ? "无" : d.getLastSeenAt().format(TIME)).append("\n");
            data.add(Map.of("name", d.getName(), "mn", d.getMn(), "site", resolve.siteName(d.getSiteId()),
                    "online", d.isOnline()));
        }
        return new ToolResult(sb.toString(), data);
    }

    private ToolResult latestValues(JsonNode args, ToolContext ctx) {
        List<Device> list = resolve.devices(text(args, "place"));
        if (list.isEmpty()) {
            return new ToolResult("没有找到这台设备。", List.of());
        }
        List<DataQueryService.DeviceLatest> latest = dataQuery.latest(list.stream().limit(5).toList());
        StringBuilder sb = new StringBuilder();
        for (DataQueryService.DeviceLatest d : latest) {
            sb.append("**").append(d.name()).append("**（").append(d.online() ? "在线" : "离线").append("）\n");
            for (DataQueryService.Value v : d.values()) {
                sb.append("- ").append(v.name()).append("：").append(v.value() == null ? "暂无数据" : v.value() + " " + v.unit())
                        .append(v.overLimit() ? " ⚠ 超标" : "").append("\n");
            }
            sb.append("\n");
        }
        return new ToolResult(sb.toString().trim(), latest);
    }

    private ToolResult queryHistory(JsonNode args, ToolContext ctx) {
        List<Device> list = resolve.devices(text(args, "place"));
        if (list.isEmpty()) {
            return new ToolResult("没有找到这台设备，请说一下站点名称或 MN 号。", null);
        }
        Device d = list.get(0);
        String factor = resolve.factor(text(args, "factor"));
        if (factor == null) {
            if (d.getFactorCodes().isEmpty()) {
                return new ToolResult("这台设备没有配置监测因子。", null);
            }
            factor = d.getFactorCodes().get(0);
        }
        int hours = Math.max(1, Math.min(args.path("hours").asInt(24), 24 * 31));
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusHours(hours);
        DataQueryService.Series s = dataQuery.series(d, factor, hours > 48 ? DataType.HOUR : DataType.MINUTE, from, to);
        if (s.count() < 3) {
            s = dataQuery.series(d, factor, DataType.REALTIME, from, to);
        }
        if (s.count() == 0) {
            return new ToolResult(d.getName() + " 最近 " + hours + " 小时没有" + factors.name(factor) + "数据。", null);
        }
        List<Object[]> points = s.points().stream().map(p -> new Object[]{p.t().toString(), p.v()}).toList();
        ctx.addChart(new ToolContext.Chart(d.getName() + " " + s.factorName() + "（最近 " + hours + " 小时）", s.unit(),
                s.upper(), s.lower(), points));
        String text = "**" + d.getName() + " " + s.factorName() + "** 最近 " + hours + " 小时（" + s.count() + " 个数据）：\n\n"
                + "- 平均 " + s.avg() + " " + s.unit() + "，最小 " + s.min() + "，最大 " + s.max() + "\n"
                + (s.upper() != null || s.lower() != null
                ? "- 限值 " + (s.lower() == null ? "" : s.lower() + " ~ ") + (s.upper() == null ? "" : s.upper())
                + "，超出限值 " + s.overCount() + " 次\n" : "");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("device", d.getName());
        data.put("factor", s.factorName());
        data.put("unit", s.unit());
        data.put("avg", s.avg());
        data.put("min", s.min());
        data.put("max", s.max());
        data.put("count", s.count());
        data.put("overCount", s.overCount());
        return new ToolResult(text, data);
    }

    private ToolResult listOrders(JsonNode args, ToolContext ctx) {
        String box = text(args, "box");
        User u = ctx.user();
        List<WorkOrder> list = "review".equals(box)
                ? orders.findAll().stream().filter(o -> o.getStatus() == WorkOrder.Status.SUBMITTED)
                .filter(o -> orderService.canReview(o, u)).sorted(Comparator.comparing(WorkOrder::getId).reversed()).toList()
                : orders.findByHandlerIdAndStatusOrderByIdDesc(u.getId(), WorkOrder.Status.PROCESSING);
        if (list.isEmpty()) {
            return new ToolResult("review".equals(box) ? "没有待你审核的工单。" : "你现在没有待办工单。", List.of());
        }
        StringBuilder sb = new StringBuilder("共 " + list.size() + " 张工单：\n\n");
        List<Map<String, Object>> data = new ArrayList<>();
        for (WorkOrder o : list) {
            sb.append("- **").append(o.getCode()).append("** ").append(o.getTitle()).append("（").append(o.getStatus().label())
                    .append("，处理人 ").append(o.getHandlerName()).append("）\n");
            data.add(Map.of("code", o.getCode(), "title", o.getTitle(), "status", o.getStatus().label(),
                    "handler", o.getHandlerName()));
        }
        return new ToolResult(sb.toString(), data);
    }

    static String text(JsonNode args, String key) {
        JsonNode n = args.path(key);
        return n.isMissingNode() || n.isNull() ? null : n.asText();
    }

    private static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static Map<String, Object> schema(Map<String, Object> props, String... required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", props);
        m.put("required", List.of(required));
        return m;
    }
}
