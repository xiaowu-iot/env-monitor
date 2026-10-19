package io.github.xiaowuiot.envmonitor.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.github.xiaowuiot.envmonitor.alarm.Alarm;
import io.github.xiaowuiot.envmonitor.alarm.AlarmRepository;
import io.github.xiaowuiot.envmonitor.alarm.AlarmStatus;
import io.github.xiaowuiot.envmonitor.device.Device;
import io.github.xiaowuiot.envmonitor.device.DeviceRepository;
import io.github.xiaowuiot.envmonitor.order.WorkOrder;
import io.github.xiaowuiot.envmonitor.order.WorkOrderRepository;

/**
 * 今日运行概况：首页和智能助手回答"今天怎么样"都用它。
 */
@Service
public class ReportService {

    private final DeviceRepository devices;
    private final AlarmRepository alarms;
    private final WorkOrderRepository orders;

    public ReportService(DeviceRepository devices, AlarmRepository alarms, WorkOrderRepository orders) {
        this.devices = devices;
        this.alarms = alarms;
        this.orders = orders;
    }

    public record Summary(LocalDateTime from, LocalDateTime to, int deviceTotal, int deviceOnline, int alarmTotal,
                          List<Map.Entry<String, Long>> topSites, int openAlarms, int pendingDispatch,
                          int ordersCreated, int ordersClosed) {
    }

    public Summary today() {
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to = LocalDateTime.now();
        List<Device> ds = devices.findAll();
        List<Alarm> as = alarms.findByFirstAtBetween(from, to);
        List<Alarm> open = alarms.findByStatusNot(AlarmStatus.CLOSED);
        List<WorkOrder> os = orders.findByCreatedAtBetween(from, to);
        List<Map.Entry<String, Long>> top = as.stream()
                .collect(Collectors.groupingBy(a -> a.getSiteName() == null ? "未分配站点" : a.getSiteName(), Collectors.counting()))
                .entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())).limit(3)
                .map(e -> Map.entry(e.getKey(), e.getValue())).toList();
        return new Summary(from, to, ds.size(), (int) ds.stream().filter(Device::isOnline).count(), as.size(), top,
                open.size(), (int) open.stream().filter(a -> a.getStatus() == AlarmStatus.PENDING).count(),
                os.size(), (int) os.stream().filter(o -> o.getStatus() == WorkOrder.Status.APPROVED).count());
    }

    public String markdown(Summary s) {
        StringBuilder sb = new StringBuilder("### 今日运行概况\n");
        sb.append("**设备**：共 ").append(s.deviceTotal()).append(" 台，当前在线 ").append(s.deviceOnline()).append(" 台\n\n");
        sb.append("**预警**：今天新增 ").append(s.alarmTotal()).append(" 条，目前未关闭 ").append(s.openAlarms()).append(" 条");
        if (s.pendingDispatch() > 0) {
            sb.append("，其中 ").append(s.pendingDispatch()).append(" 条还没派单");
        }
        sb.append("\n\n");
        if (!s.topSites().isEmpty()) {
            sb.append("**预警最多的站点**：").append(s.topSites().stream().map(e -> e.getKey() + "（" + e.getValue() + "）")
                    .collect(Collectors.joining("、"))).append("\n\n");
        }
        sb.append("**工单**：今天新建 ").append(s.ordersCreated()).append(" 张，已办结 ").append(s.ordersClosed()).append(" 张\n");
        return sb.toString();
    }
}
