package io.github.xiaowuiot.envmonitor.alarm;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.xiaowuiot.envmonitor.common.ApiException;
import io.github.xiaowuiot.envmonitor.common.PageResult;
import io.github.xiaowuiot.envmonitor.common.Via;
import io.github.xiaowuiot.envmonitor.data.DataType;
import io.github.xiaowuiot.envmonitor.order.DispatchService;
import io.github.xiaowuiot.envmonitor.order.WorkOrder;
import io.github.xiaowuiot.envmonitor.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 预警列表、手动派单、预警规则。
 */
@RestController
@RequestMapping("/api")
public class AlarmController {

    private final AlarmRepository alarms;
    private final AlarmRuleRepository rules;
    private final AlarmEngine engine;
    private final DispatchService dispatch;
    private final CurrentUser current;

    public AlarmController(AlarmRepository alarms, AlarmRuleRepository rules, AlarmEngine engine,
                           DispatchService dispatch, CurrentUser current) {
        this.alarms = alarms;
        this.rules = rules;
        this.engine = engine;
        this.dispatch = dispatch;
        this.current = current;
    }

    public record AlarmView(Long id, Long ruleId, String ruleName, String type, String typeLabel, int level,
                            String levelText, Long deviceId, String mn, String deviceName, Long siteId, String siteName,
                            String factorCode, String factorName, Double value, Double peakValue, String threshold,
                            String content, LocalDateTime firstAt, LocalDateTime lastAt, int hitCount, String status,
                            String statusLabel, boolean recovered, LocalDateTime recoveredAt, Long orderId,
                            LocalDateTime closedAt) {
        public static AlarmView of(Alarm a) {
            return new AlarmView(a.getId(), a.getRuleId(), a.getRuleName(), a.getType().name(), a.getType().label(),
                    a.getLevel(), a.levelText(), a.getDeviceId(), a.getMn(), a.getDeviceName(), a.getSiteId(),
                    a.getSiteName(), a.getFactorCode(), a.getFactorName(), a.getValue(), a.getPeakValue(),
                    a.getThreshold(), a.getContent(), a.getFirstAt(), a.getLastAt(), a.getHitCount(),
                    a.getStatus().name(), a.getStatus().label(), a.isRecovered(), a.getRecoveredAt(), a.getOrderId(),
                    a.getClosedAt());
        }
    }

    @GetMapping("/alarms")
    @PreAuthorize("hasAuthority('PERM_ALARM_VIEW')")
    public PageResult<AlarmView> list(@RequestParam(name = "status", required = false) AlarmStatus status,
                                      @RequestParam(name = "open", required = false) Boolean open,
                                      @RequestParam(name = "level", required = false) Integer level,
                                      @RequestParam(name = "siteId", required = false) Long siteId,
                                      @RequestParam(name = "deviceId", required = false) Long deviceId,
                                      @RequestParam(name = "keyword", required = false) String keyword,
                                      @RequestParam(name = "from", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
                                      @RequestParam(name = "to", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
                                      @RequestParam(name = "page", defaultValue = "0") int page,
                                      @RequestParam(name = "size", defaultValue = "20") int size) {
        Specification<Alarm> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (Boolean.TRUE.equals(open)) {
                ps.add(cb.notEqual(root.get("status"), AlarmStatus.CLOSED));
            }
            if (level != null) {
                ps.add(cb.equal(root.get("level"), level));
            }
            if (siteId != null) {
                ps.add(cb.equal(root.get("siteId"), siteId));
            }
            if (deviceId != null) {
                ps.add(cb.equal(root.get("deviceId"), deviceId));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("firstAt"), from));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("firstAt"), to));
            }
            if (keyword != null && !keyword.isBlank()) {
                String k = "%" + keyword.trim() + "%";
                ps.add(cb.or(cb.like(root.get("siteName"), k), cb.like(root.get("deviceName"), k),
                        cb.like(root.get("mn"), k), cb.like(root.get("content"), k)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return PageResult.of(alarms.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"))),
                AlarmView::of);
    }

    @GetMapping("/alarms/{id}")
    @PreAuthorize("hasAuthority('PERM_ALARM_VIEW')")
    public AlarmView get(@PathVariable Long id) {
        return AlarmView.of(alarms.findById(id).orElseThrow(() -> ApiException.notFound("预警不存在")));
    }

    public record DispatchRequest(@NotNull(message = "请选择处理人") Long handlerId, Long reviewerId) {
    }

    /** 站点没有负责人的预警停在"待派单"，由有派单权限的人手动派 */
    @PostMapping("/alarms/{id}/dispatch")
    @PreAuthorize("hasAuthority('PERM_ORDER_DISPATCH')")
    @Transactional
    public Map<String, Object> dispatch(@PathVariable Long id, @Valid @RequestBody DispatchRequest body) {
        Alarm a = alarms.findById(id).orElseThrow(() -> ApiException.notFound("预警不存在"));
        WorkOrder o = dispatch.manualDispatch(a, body.handlerId(), body.reviewerId(), current.get(), Via.WEB);
        return Map.of("orderId", o.getId(), "code", o.getCode());
    }

    // ===================== 预警规则 =====================

    public record RuleRequest(@NotBlank(message = "请输入规则名称") String name,
                              @NotBlank(message = "请选择监测因子") String factorCode, DataType dataType,
                              Double upperLimit, Double lowerLimit, Integer level, Long siteId, Long deviceId,
                              Boolean enabled, String remark) {
    }

    public record RuleView(AlarmRule rule, String thresholdText) {
    }

    @GetMapping("/alarm-rules")
    @PreAuthorize("hasAuthority('PERM_ALARM_VIEW')")
    public List<RuleView> rules() {
        return rules.findAllByOrderByIdAsc().stream().map(r -> new RuleView(r, r.thresholdText())).toList();
    }

    @PostMapping("/alarm-rules")
    @PreAuthorize("hasAuthority('PERM_RULE_MANAGE')")
    public AlarmRule createRule(@Valid @RequestBody RuleRequest body) {
        AlarmRule r = new AlarmRule();
        apply(r, body);
        AlarmRule saved = rules.save(r);
        engine.reloadRules();
        return saved;
    }

    @PutMapping("/alarm-rules/{id}")
    @PreAuthorize("hasAuthority('PERM_RULE_MANAGE')")
    public AlarmRule updateRule(@PathVariable Long id, @Valid @RequestBody RuleRequest body) {
        AlarmRule r = rules.findById(id).orElseThrow(() -> ApiException.notFound("规则不存在"));
        apply(r, body);
        AlarmRule saved = rules.save(r);
        engine.reloadRules();
        return saved;
    }

    @DeleteMapping("/alarm-rules/{id}")
    @PreAuthorize("hasAuthority('PERM_RULE_MANAGE')")
    public Map<String, Boolean> deleteRule(@PathVariable Long id) {
        rules.deleteById(id);
        engine.reloadRules();
        return Map.of("ok", true);
    }

    private static void apply(AlarmRule r, RuleRequest b) {
        if (b.upperLimit() == null && b.lowerLimit() == null) {
            throw ApiException.badRequest("上限和下限至少填一个");
        }
        r.setName(b.name().strip());
        r.setFactorCode(b.factorCode());
        r.setDataType(b.dataType() == null ? DataType.REALTIME : b.dataType());
        r.setUpperLimit(b.upperLimit());
        r.setLowerLimit(b.lowerLimit());
        r.setLevel(b.level() == null ? 2 : Math.max(1, Math.min(3, b.level())));
        r.setSiteId(b.siteId());
        r.setDeviceId(b.deviceId());
        r.setEnabled(b.enabled() == null || b.enabled());
        r.setRemark(b.remark());
    }
}
