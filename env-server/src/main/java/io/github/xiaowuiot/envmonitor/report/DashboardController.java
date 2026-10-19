package io.github.xiaowuiot.envmonitor.report;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.xiaowuiot.envmonitor.alarm.Alarm;
import io.github.xiaowuiot.envmonitor.alarm.AlarmController;
import io.github.xiaowuiot.envmonitor.alarm.AlarmRepository;
import io.github.xiaowuiot.envmonitor.alarm.AlarmStatus;
import io.github.xiaowuiot.envmonitor.order.WorkOrder;
import io.github.xiaowuiot.envmonitor.order.WorkOrderRepository;
import io.github.xiaowuiot.envmonitor.security.CurrentUser;
import io.github.xiaowuiot.envmonitor.user.User;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final ReportService reports;
    private final AlarmRepository alarms;
    private final WorkOrderRepository orders;
    private final CurrentUser current;

    public DashboardController(ReportService reports, AlarmRepository alarms, WorkOrderRepository orders,
                               CurrentUser current) {
        this.reports = reports;
        this.alarms = alarms;
        this.orders = orders;
        this.current = current;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        User u = current.get();
        List<AlarmController.AlarmView> recent = alarms.findByStatusNot(AlarmStatus.CLOSED).stream()
                .sorted((a, b) -> b.getLastAt().compareTo(a.getLastAt())).limit(8).map(AlarmController.AlarmView::of).toList();
        // 最近 7 天每天的新增预警数
        Map<String, Long> trend = new LinkedHashMap<>();
        LocalDate start = LocalDate.now().minusDays(6);
        for (int i = 0; i < 7; i++) {
            trend.put(start.plusDays(i).toString().substring(5), 0L);
        }
        for (Alarm a : alarms.findByFirstAtBetween(start.atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay())) {
            trend.merge(a.getFirstAt().toLocalDate().toString().substring(5), 1L, Long::sum);
        }
        List<Map<String, Object>> trendList = new ArrayList<>();
        trend.forEach((d, n) -> trendList.add(Map.of("date", d, "count", n)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("summary", reports.today());
        m.put("recentAlarms", recent);
        m.put("trend", trendList);
        m.put("myTodo", orders.findByHandlerIdAndStatusOrderByIdDesc(u.getId(), WorkOrder.Status.PROCESSING).size());
        m.put("myReview", orders.findByReviewerIdAndStatusOrderByIdDesc(u.getId(), WorkOrder.Status.SUBMITTED).size());
        return m;
    }
}
