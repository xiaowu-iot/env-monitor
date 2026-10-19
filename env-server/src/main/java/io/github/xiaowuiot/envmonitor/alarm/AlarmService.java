package io.github.xiaowuiot.envmonitor.alarm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.xiaowuiot.envmonitor.common.Via;
import io.github.xiaowuiot.envmonitor.device.Device;
import io.github.xiaowuiot.envmonitor.device.FactorService;
import io.github.xiaowuiot.envmonitor.order.DispatchService;
import io.github.xiaowuiot.envmonitor.order.WorkOrderLog;
import io.github.xiaowuiot.envmonitor.order.WorkOrderRepository;
import io.github.xiaowuiot.envmonitor.order.WorkOrderService;
import io.github.xiaowuiot.envmonitor.site.Site;
import io.github.xiaowuiot.envmonitor.site.SiteRepository;

/**
 * 预警的产生、合并和恢复。
 */
@Service
public class AlarmService {

    private static final Logger log = LoggerFactory.getLogger(AlarmService.class);

    private final AlarmRepository alarms;
    private final SiteRepository sites;
    private final FactorService factors;
    private final DispatchService dispatch;
    private final WorkOrderRepository orders;
    private final WorkOrderService orderService;

    public AlarmService(AlarmRepository alarms, SiteRepository sites, FactorService factors, DispatchService dispatch,
                        WorkOrderRepository orders, WorkOrderService orderService) {
        this.alarms = alarms;
        this.sites = sites;
        this.factors = factors;
        this.dispatch = dispatch;
        this.orders = orders;
        this.orderService = orderService;
    }

    /**
     * 触发一次预警。已经有未关闭的同类预警时只累加次数，不重复派单
     */
    @Transactional
    public Alarm raise(AlarmRule rule, Device device, String factorCode, Double value, String content) {
        Alarm open = alarms.findOpen(rule.getId(), device.getId(), factorCode).orElse(null);
        if (open != null) {
            open.incrementHit();
            open.hit(value, content);
            return alarms.save(open);
        }
        Alarm a = new Alarm(rule, device.getId(), device.getMn(), device.getName());
        a.setFactorCode(factorCode);
        a.setFactorName(factors.name(factorCode));
        Site site = device.getSiteId() == null ? null : sites.findById(device.getSiteId()).orElse(null);
        if (site != null) {
            a.setSiteId(site.getId());
            a.setSiteName(site.getName());
        } else {
            a.setSiteName("未分配站点");
        }
        a.hit(value, content);
        alarms.save(a);
        log.info("产生预警 #{} {} {}", a.getId(), device.getMn(), content);
        dispatch.autoDispatch(a);
        return a;
    }

    /**
     * 数据恢复正常：标记已恢复，但预警仍然要走完处理和审核才关闭
     */
    @Transactional
    public void recover(Long ruleId, Long deviceId, String factorCode, String reason) {
        alarms.findOpen(ruleId, deviceId, factorCode).filter(a -> !a.isRecovered()).ifPresent(a -> {
            a.markRecovered();
            alarms.save(a);
            if (a.getOrderId() != null) {
                orders.findById(a.getOrderId()).ifPresent(o ->
                        orderService.log(o, WorkOrderLog.Action.RECOVER, null, reason, Via.SYSTEM));
            }
        });
    }
}
