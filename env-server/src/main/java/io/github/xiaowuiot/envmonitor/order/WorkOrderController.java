package io.github.xiaowuiot.envmonitor.order;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.xiaowuiot.envmonitor.alarm.AlarmController;
import io.github.xiaowuiot.envmonitor.alarm.AlarmRepository;
import io.github.xiaowuiot.envmonitor.common.PageResult;
import io.github.xiaowuiot.envmonitor.common.Via;
import io.github.xiaowuiot.envmonitor.security.CurrentUser;
import io.github.xiaowuiot.envmonitor.user.Permission;
import io.github.xiaowuiot.envmonitor.user.User;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 工单：我的待办、待我审核、全部；处理、审核。
 */
@RestController
@RequestMapping("/api")
public class WorkOrderController {

    private final WorkOrderRepository orders;
    private final WorkOrderService service;
    private final AlarmRepository alarms;
    private final CurrentUser current;

    public WorkOrderController(WorkOrderRepository orders, WorkOrderService service, AlarmRepository alarms,
                               CurrentUser current) {
        this.orders = orders;
        this.service = service;
        this.alarms = alarms;
        this.current = current;
    }

    public record OrderView(Long id, String code, String title, Long alarmId, Long siteId, Long deviceId,
                            Long handlerId, String handlerName, Long reviewerId, String reviewerName, String status,
                            String statusLabel, String dispatchReason, int rejectCount, String resultText,
                            LocalDateTime createdAt, LocalDateTime submittedAt, LocalDateTime closedAt) {
        public static OrderView of(WorkOrder o) {
            return new OrderView(o.getId(), o.getCode(), o.getTitle(), o.getAlarmId(), o.getSiteId(), o.getDeviceId(),
                    o.getHandlerId(), o.getHandlerName(), o.getReviewerId(), o.getReviewerName(), o.getStatus().name(),
                    o.getStatus().label(), o.getDispatchReason(), o.getRejectCount(), o.getResultText(),
                    o.getCreatedAt(), o.getSubmittedAt(), o.getClosedAt());
        }
    }

    public record LogView(Long id, String action, String actionLabel, String operatorName, String content, String via,
                          LocalDateTime createdAt) {
        static LogView of(WorkOrderLog l) {
            return new LogView(l.getId(), l.getAction().name(), l.getAction().label(), l.getOperatorName(),
                    l.getContent(), l.getVia() == null ? null : l.getVia().label(), l.getCreatedAt());
        }
    }

    public record OrderDetail(OrderView order, AlarmController.AlarmView alarm, List<LogView> logs, boolean canSubmit,
                              boolean canReview) {
    }

    /**
     * @param box todo 我的待办、review 待我审核、mine 和我有关的、all 全部
     */
    @GetMapping("/orders")
    public PageResult<OrderView> list(@RequestParam(name = "box", defaultValue = "todo") String box,
                                      @RequestParam(name = "status", required = false) WorkOrder.Status status,
                                      @RequestParam(name = "keyword", required = false) String keyword,
                                      @RequestParam(name = "page", defaultValue = "0") int page,
                                      @RequestParam(name = "size", defaultValue = "20") int size) {
        User u = current.get();
        boolean canSeeAll = u.has(Permission.ALARM_VIEW);
        Specification<WorkOrder> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            switch (box) {
                case "todo" -> {
                    ps.add(cb.equal(root.get("handlerId"), u.getId()));
                    ps.add(cb.equal(root.get("status"), WorkOrder.Status.PROCESSING));
                }
                case "review" -> {
                    ps.add(cb.equal(root.get("status"), WorkOrder.Status.SUBMITTED));
                    Predicate mine = cb.equal(root.get("reviewerId"), u.getId());
                    ps.add(u.has(Permission.ORDER_REVIEW) ? cb.or(mine, cb.isNull(root.get("reviewerId"))) : mine);
                }
                case "mine" -> ps.add(cb.or(cb.equal(root.get("handlerId"), u.getId()),
                        cb.equal(root.get("reviewerId"), u.getId())));
                default -> {
                    if (!canSeeAll) {
                        ps.add(cb.or(cb.equal(root.get("handlerId"), u.getId()), cb.equal(root.get("reviewerId"), u.getId())));
                    }
                }
            }
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String k = "%" + keyword.trim() + "%";
                ps.add(cb.or(cb.like(root.get("code"), k), cb.like(root.get("title"), k), cb.like(root.get("handlerName"), k)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return PageResult.of(orders.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"))),
                OrderView::of);
    }

    @GetMapping("/orders/{id}")
    public OrderDetail detail(@PathVariable Long id) {
        User u = current.get();
        WorkOrder o = service.get(id);
        service.checkVisible(o, u);
        AlarmController.AlarmView alarm = alarms.findById(o.getAlarmId()).map(AlarmController.AlarmView::of).orElse(null);
        boolean canSubmit = o.getStatus() == WorkOrder.Status.PROCESSING && u.getId().equals(o.getHandlerId());
        boolean canReview = o.getStatus() == WorkOrder.Status.SUBMITTED && service.canReview(o, u);
        return new OrderDetail(OrderView.of(o), alarm, service.logs(o.getId()).stream().map(LogView::of).toList(),
                canSubmit, canReview);
    }

    public record SubmitRequest(@NotBlank(message = "请填写处理结果") String result) {
    }

    public record ReviewRequest(@NotNull Boolean approve, String comment) {
    }

    @PostMapping("/orders/{id}/submit")
    @PreAuthorize("hasAuthority('PERM_ORDER_HANDLE')")
    public OrderView submit(@PathVariable Long id, @Valid @RequestBody SubmitRequest body) {
        return OrderView.of(service.submit(id, body.result(), current.get(), Via.WEB));
    }

    @PostMapping("/orders/{id}/review")
    @PreAuthorize("hasAuthority('PERM_ORDER_REVIEW')")
    public OrderView review(@PathVariable Long id, @Valid @RequestBody ReviewRequest body) {
        return OrderView.of(service.review(id, body.approve(), body.comment(), current.get(), Via.WEB));
    }
}
