package io.github.xiaowuiot.envmonitor.device;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.xiaowuiot.envmonitor.config.EnvProperties;
import io.github.xiaowuiot.envmonitor.ingest.SessionRegistry;
import jakarta.annotation.PostConstruct;

/**
 * 设备在线状态。
 * <ul>
 *     <li>收到报文就算在线；为了不每条报文都写库，最后上报时间每 15 秒最多落一次库</li>
 *     <li>连接断开立即离线；连接还在、但超过 env.device.offline-timeout-seconds 没有任何报文，也判离线并断开连接</li>
 * </ul>
 */
@Service
public class DeviceStateService {

    private static final Logger log = LoggerFactory.getLogger(DeviceStateService.class);
    private static final long PERSIST_INTERVAL_SECONDS = 15;

    private final DeviceRepository devices;
    private final SessionRegistry sessions;
    private final ApplicationEventPublisher events;
    private final EnvProperties props;
    private final Map<Long, LocalDateTime> lastPersisted = new ConcurrentHashMap<>();

    public DeviceStateService(DeviceRepository devices, SessionRegistry sessions, ApplicationEventPublisher events,
                              EnvProperties props) {
        this.devices = devices;
        this.sessions = sessions;
        this.events = events;
        this.props = props;
    }

    /**
     * 平台重启后，库里的在线状态都不可信，全部置为离线，等设备重新连上来
     */
    @PostConstruct
    void resetOnStartup() {
        int n = devices.markAllOffline();
        if (n > 0) {
            log.info("启动时把 {} 台设备置为离线，等待重新连接", n);
        }
    }

    @Transactional
    public void touch(Long deviceId, String remote, String protocolVersion) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last = lastPersisted.get(deviceId);
        Device d = null;
        if (last == null || last.plusSeconds(PERSIST_INTERVAL_SECONDS).isBefore(now)) {
            d = devices.findById(deviceId).orElse(null);
        }
        if (d == null) {
            return;
        }
        boolean wasOnline = d.isOnline();
        d.setLastSeenAt(now);
        d.setLastIp(remote);
        d.setProtocolVersion(protocolVersion);
        if (!wasOnline) {
            d.setOnline(true);
            d.setOnlineAt(now);
        }
        devices.save(d);
        lastPersisted.put(deviceId, now);
        if (!wasOnline) {
            log.info("设备上线 {} {}", d.getMn(), remote);
            events.publishEvent(new DeviceEvents.Online(d.getId(), d.getMn()));
        }
    }

    /**
     * 新连接上来时调用：强制落一次库，保证上线事件及时触发
     */
    public void connected(Long deviceId, String remote, String protocolVersion) {
        lastPersisted.remove(deviceId);
        touch(deviceId, remote, protocolVersion);
    }

    @Transactional
    public void offline(String mn, String reason) {
        devices.findByMn(mn).ifPresent(d -> {
            lastPersisted.remove(d.getId());
            if (!d.isOnline()) {
                return;
            }
            d.setOnline(false);
            d.setOfflineAt(LocalDateTime.now());
            devices.save(d);
            log.info("设备离线 {}：{}", mn, reason);
            events.publishEvent(new DeviceEvents.Offline(d.getId(), d.getMn(), reason));
        });
    }

    /**
     * 连接还在、但很久没有报文的，判离线并断开连接
     */
    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void checkTimeout() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(props.device().offlineTimeoutSeconds());
        for (Device d : devices.findByOnlineTrueAndLastSeenAtBefore(deadline)) {
            // 最后上报时间是节流落库的，以连接的实际状态为准再判断一次
            LocalDateTime last = lastPersisted.get(d.getId());
            if (last != null && last.isAfter(deadline) && sessions.isOnline(d.getMn())) {
                continue;
            }
            sessions.close(d.getMn());
            offline(d.getMn(), "超过 " + props.device().offlineTimeoutSeconds() + " 秒没有上报数据");
        }
    }
}
