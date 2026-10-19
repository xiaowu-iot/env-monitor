package io.github.xiaowuiot.envmonitor.order;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.xiaowuiot.envmonitor.alarm.Alarm;
import io.github.xiaowuiot.envmonitor.alarm.AlarmStatus;
import io.github.xiaowuiot.envmonitor.common.ApiException;
import io.github.xiaowuiot.envmonitor.common.Via;
import io.github.xiaowuiot.envmonitor.notify.NotifyEvent;
import io.github.xiaowuiot.envmonitor.notify.NotifyService;
import io.github.xiaowuiot.envmonitor.site.Site;
import io.github.xiaowuiot.envmonitor.site.SiteRepository;
import io.github.xiaowuiot.envmonitor.user.Permission;
import io.github.xiaowuiot.envmonitor.user.User;
import io.github.xiaowuiot.envmonitor.user.UserRepository;

/**
 * 自动派单：预警派给所在站点的负责人，由站点审核人审核。站点没配负责人的，预警停在"待派单"，提醒有派单权限的人手动派。
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final UserRepository users;
    private final SiteRepository sites;
    private final WorkOrderService orderService;
    private final NotifyService notify;

    public DispatchService(UserRepository users, SiteRepository sites, WorkOrderService orderService, NotifyService notify) {
        this.users = users;
        this.sites = sites;
        this.orderService = orderService;
        this.notify = notify;
    }

    /**
     * @return 建好的工单；找不到处理人返回 null
     */
    @Transactional
    public WorkOrder autoDispatch(Alarm alarm) {
        Site site = alarm.getSiteId() == null ? null : sites.findById(alarm.getSiteId()).orElse(null);
        User handler = site == null || site.getHandlerId() == null ? null
                : users.findById(site.getHandlerId()).filter(User::isEnabled).orElse(null);
        if (handler == null) {
            log.info("预警 {} 所在站点没有负责人，等待手动派单", alarm.getId());
            notify.publish(NotifyEvent.ALARM_UNASSIGNED, WorkOrderService.alarmVars(alarm),
                    notify.userIdsWith(Permission.ORDER_DISPATCH), List.of(), null);
            return null;
        }
        User reviewer = site.getReviewerId() == null ? null
                : users.findById(site.getReviewerId()).filter(User::isEnabled).orElse(null);
        return orderService.create(alarm, handler, reviewer, "站点负责人", null, Via.SYSTEM);
    }

    @Transactional
    public WorkOrder manualDispatch(Alarm alarm, Long handlerId, Long reviewerId, User operator, Via via) {
        if (alarm.getStatus() != AlarmStatus.PENDING) {
            throw ApiException.badRequest("这条预警已经在处理了");
        }
        User handler = users.findById(handlerId).filter(User::isEnabled)
                .orElseThrow(() -> ApiException.badRequest("处理人不存在或已停用"));
        if (!handler.has(Permission.ORDER_HANDLE)) {
            throw ApiException.badRequest(handler.getName() + " 没有处理工单的权限");
        }
        User reviewer = null;
        if (reviewerId != null) {
            reviewer = users.findById(reviewerId).filter(User::isEnabled)
                    .orElseThrow(() -> ApiException.badRequest("审核人不存在或已停用"));
            if (!reviewer.has(Permission.ORDER_REVIEW)) {
                throw ApiException.badRequest(reviewer.getName() + " 没有审核权限");
            }
        }
        return orderService.create(alarm, handler, reviewer, "手动派单", operator, via);
    }
}
