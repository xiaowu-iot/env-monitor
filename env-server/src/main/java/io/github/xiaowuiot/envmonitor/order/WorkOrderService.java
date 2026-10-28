package io.github.xiaowuiot.envmonitor.order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.xiaowuiot.envmonitor.alarm.Alarm;
import io.github.xiaowuiot.envmonitor.alarm.AlarmRepository;
import io.github.xiaowuiot.envmonitor.alarm.AlarmStatus;
import io.github.xiaowuiot.envmonitor.common.ApiException;
import io.github.xiaowuiot.envmonitor.common.Via;
import io.github.xiaowuiot.envmonitor.notify.NotifyEvent;
import io.github.xiaowuiot.envmonitor.notify.NotifyService;
import io.github.xiaowuiot.envmonitor.user.Permission;
import io.github.xiaowuiot.envmonitor.user.User;
import io.github.xiaowuiot.envmonitor.user.UserRepository;

/**
 * 工单流转：派单 → 处理 → 提交 → 审核（通过 / 驳回）。每一步都写流转记录、同步预警状态、发通知。
 */
@Service
public class WorkOrderService {

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final WorkOrderRepository orders;
    private final WorkOrderLogRepository logs;
    private final AlarmRepository alarms;
    private final UserRepository users;
    private final NotifyService notify;

    public WorkOrderService(WorkOrderRepository orders, WorkOrderLogRepository logs, AlarmRepository alarms,
                            UserRepository users, NotifyService notify) {
        this.orders = orders;
        this.logs = logs;
        this.alarms = alarms;
        this.users = users;
        this.notify = notify;
    }

    /**
     * 给预警建工单
     *
     * @param operator 手动派单的人；系统自动派单时为 null
     */
    @Transactional
    public WorkOrder create(Alarm alarm, User handler, User reviewer, String reason, User operator, Via via) {
        if (alarm.getOrderId() != null) {
            throw ApiException.badRequest("这条预警已经派过单了");
        }
        String title = alarm.getSiteName() + " " + (alarm.getFactorName() == null ? "" : alarm.getFactorName())
                + alarm.getType().label();
        WorkOrder o = new WorkOrder(nextCode(), alarm.getId(), title.trim());
        o.setSiteId(alarm.getSiteId());
        o.setDeviceId(alarm.getDeviceId());
        o.setHandler(handler.getId(), handler.getName());
        if (reviewer != null) {
            o.setReviewer(reviewer.getId(), reviewer.getName());
        }
        o.setDispatchReason(reason);
        orders.save(o);
        alarm.setOrderId(o.getId());
        alarm.setStatus(AlarmStatus.PROCESSING);
        alarms.save(alarm);

        String who = operator == null ? "系统" : operator.getName();
        log(o, WorkOrderLog.Action.CREATE, operator, who + "派单给 " + handler.getName()
                + (reviewer == null ? "" : "，审核人 " + reviewer.getName()) + "（" + reason + "）", via);
        notify.publish(NotifyEvent.ORDER_CREATED, vars(o, alarm), List.of(handler.getId()), List.of(handler), o.getId());
        log(o, WorkOrderLog.Action.NOTIFY, null, "已通知处理人 " + handler.getName(), Via.SYSTEM);
        return o;
    }

    @Transactional
    public WorkOrder submit(Long orderId, String result, User operator, Via via) {
        WorkOrder o = get(orderId);
        if (o.getStatus() != WorkOrder.Status.PROCESSING) {
            throw ApiException.badRequest("工单当前是「" + o.getStatus().label() + "」，不能提交");
        }
        if (!Objects.equals(o.getHandlerId(), operator.getId())) {
            throw ApiException.forbidden("只有处理人 " + o.getHandlerName() + " 可以提交处理结果");
        }
        if (result == null || result.isBlank()) {
            throw ApiException.badRequest("请填写处理结果");
        }
        o.setStatus(WorkOrder.Status.SUBMITTED);
        o.setResultText(result.trim());
        o.setSubmittedAt(LocalDateTime.now());
        orders.save(o);
        Alarm alarm = alarms.findById(o.getAlarmId()).orElse(null);
        if (alarm != null) {
            alarm.setStatus(AlarmStatus.REVIEWING);
            alarms.save(alarm);
        }
        log(o, WorkOrderLog.Action.SUBMIT, operator, result.trim(), via);
        List<User> reviewers = reviewersOf(o);
        notify.publish(NotifyEvent.ORDER_SUBMITTED, vars(o, alarm), reviewers.stream().map(User::getId).toList(),
                reviewers, o.getId());
        return o;
    }

    @Transactional
    public WorkOrder review(Long orderId, boolean approve, String comment, User operator, Via via) {
        WorkOrder o = get(orderId);
        if (o.getStatus() != WorkOrder.Status.SUBMITTED) {
            throw ApiException.badRequest("工单当前是「" + o.getStatus().label() + "」，不能审核");
        }
        if (!canReview(o, operator)) {
            throw ApiException.forbidden(o.getReviewerId() == null ? "你没有审核权限"
                    : "只有审核人 " + o.getReviewerName() + " 可以审核");
        }
        if (!approve && (comment == null || comment.isBlank())) {
            throw ApiException.badRequest("驳回时请写明原因");
        }
        if (o.getReviewerId() == null) {
            o.setReviewer(operator.getId(), operator.getName());
        }
        Alarm alarm = alarms.findById(o.getAlarmId()).orElse(null);
        if (approve) {
            o.setStatus(WorkOrder.Status.APPROVED);
            o.setClosedAt(LocalDateTime.now());
            if (alarm != null) {
                alarm.setStatus(AlarmStatus.CLOSED);
            }
        } else {
            o.setStatus(WorkOrder.Status.PROCESSING);
            o.incrementReject();
            if (alarm != null) {
                alarm.setStatus(AlarmStatus.PROCESSING);
            }
        }
        orders.save(o);
        if (alarm != null) {
            alarms.save(alarm);
        }
        String text = comment == null || comment.isBlank() ? "" : comment.trim();
        log(o, approve ? WorkOrderLog.Action.APPROVE : WorkOrderLog.Action.REJECT, operator, text, via);
        Map<String, String> v = vars(o, alarm);
        v.put("reviewResult", approve ? "通过" : "驳回");
        v.put("reviewComment", text.isEmpty() ? "无" : text);
        User handler = users.findById(o.getHandlerId()).orElse(null);
        notify.publish(NotifyEvent.ORDER_REVIEWED, v, List.of(o.getHandlerId()),
                handler == null ? List.of() : List.of(handler), o.getId());
        return o;
    }

    /** 指定了审核人的只有他能审；没指定的，有审核权限的人都能审 */
    public boolean canReview(WorkOrder o, User u) {
        if (!u.has(Permission.ORDER_REVIEW)) {
            return false;
        }
        return o.getReviewerId() == null || o.getReviewerId().equals(u.getId());
    }

    public WorkOrder get(Long id) {
        return orders.findById(id).orElseThrow(() -> ApiException.notFound("工单不存在"));
    }

    /**
     * 查看权限：处理人、审核人、能看预警的人
     */
    public void checkVisible(WorkOrder o, User u) {
        if (Objects.equals(o.getHandlerId(), u.getId()) || Objects.equals(o.getReviewerId(), u.getId())
                || u.has(Permission.ALARM_VIEW) || u.has(Permission.ORDER_REVIEW)) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有权限查看这张工单");
    }

    public List<WorkOrderLog> logs(Long orderId) {
        return logs.findByOrderIdOrderByIdAsc(orderId);
    }

    @Transactional
    public void log(WorkOrder o, WorkOrderLog.Action action, User operator, String content, Via via) {
        logs.save(new WorkOrderLog(o.getId(), action, operator == null ? null : operator.getId(),
                operator == null ? "系统" : operator.getName(), content, via));
    }

    private List<User> reviewersOf(WorkOrder o) {
        if (o.getReviewerId() != null) {
            return users.findById(o.getReviewerId()).map(List::of).orElse(List.of());
        }
        return users.findByEnabledTrueOrderById().stream().filter(u -> u.has(Permission.ORDER_REVIEW)).toList();
    }

    private synchronized String nextCode() {
        LocalDate today = LocalDate.now();
        long n = orders.countByCreatedAtAfter(today.atStartOfDay()) + 1;
        String code;
        do {
            code = "GD" + today.format(CODE_DATE) + String.format("%04d", n++);
        } while (orders.existsByCode(code));
        return code;
    }

    public static Map<String, String> vars(WorkOrder o, Alarm a) {
        Map<String, String> v = new HashMap<>();
        v.put("orderCode", o.getCode());
        v.put("handlerName", nvl(o.getHandlerName()));
        v.put("reviewerName", o.getReviewerName() == null ? "有审核权限的人员" : o.getReviewerName());
        v.put("resultText", nvl(o.getResultText()));
        if (a != null) {
            v.putAll(alarmVars(a));
        }
        return v;
    }

    public static Map<String, String> alarmVars(Alarm a) {
        Map<String, String> v = new HashMap<>();
        v.put("siteName", nvl(a.getSiteName()));
        v.put("deviceName", nvl(a.getDeviceName()));
        v.put("mn", nvl(a.getMn()));
        v.put("factorName", nvl(a.getFactorName()));
        v.put("typeLabel", a.getType().label());
        v.put("content", nvl(a.getContent()));
        v.put("levelText", a.levelText());
        v.put("firstAt", a.getFirstAt().format(TIME));
        return v;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
