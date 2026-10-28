package io.github.xiaowuiot.envmonitor.ingest;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.xiaowuiot.envmonitor.alarm.AlarmEngine;
import io.github.xiaowuiot.envmonitor.config.EnvProperties;
import io.github.xiaowuiot.envmonitor.data.DataService;
import io.github.xiaowuiot.envmonitor.data.DataType;
import io.github.xiaowuiot.envmonitor.data.MonitorData;
import io.github.xiaowuiot.envmonitor.device.Device;
import io.github.xiaowuiot.envmonitor.device.DeviceRepository;
import io.github.xiaowuiot.envmonitor.device.DeviceStateService;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Commands;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Crypto;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Packet;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Versions;
import io.github.xiaowuiot.envmonitor.hj212.codec.Hj212Frame;
import io.netty.channel.Channel;
import jakarta.annotation.PreDestroy;

/**
 * 收到的每一帧都从这里进来：记原始报文 → 校验 → 绑定设备 → 数据入库 → 预警判断 → 回 9014。
 * <p>
 * 2005、2017、2025 版混接：按 Flag 里的版本号识别，应答用对方的版本。2025 版的加密报文在解码时已经按设备密钥解开，
 * 心跳包（9015）回 9013，补传的数据（RF=1）入库但不判断预警。
 * <p>
 * 入库、预警判断都是阻塞操作，不能放在 Netty 的 IO 线程里做。这里按 MN 把报文分到固定的单线程队列：
 * 同一台设备的报文严格按顺序处理，不同设备之间并行。
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);
    private static final int WORKERS = 4;

    private final DeviceRepository devices;
    private final DeviceStateService state;
    private final SessionRegistry sessions;
    private final RawLogger raw;
    private final DataService dataService;
    private final AlarmEngine alarmEngine;
    private final EnvProperties props;
    private final ExecutorService[] workers = new ExecutorService[WORKERS];

    public IngestService(DeviceRepository devices, DeviceStateService state, SessionRegistry sessions, RawLogger raw,
                         DataService dataService, AlarmEngine alarmEngine, EnvProperties props) {
        this.devices = devices;
        this.state = state;
        this.sessions = sessions;
        this.raw = raw;
        this.dataService = dataService;
        this.alarmEngine = alarmEngine;
        this.props = props;
        for (int i = 0; i < WORKERS; i++) {
            int n = i;
            workers[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ingest-" + n);
                t.setDaemon(true);
                return t;
            });
        }
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        for (ExecutorService w : workers) {
            w.shutdown();
        }
        for (ExecutorService w : workers) {
            w.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Netty IO 线程调用，只做分发
     */
    public void submit(Channel ch, Hj212Frame frame) {
        String mn = frame.packet() == null ? "" : frame.packet().getMn();
        workers[Math.floorMod(mn == null ? 0 : mn.hashCode(), WORKERS)].execute(() -> {
            try {
                handle(ch, frame);
            } catch (RuntimeException e) {
                log.error("处理报文出错：{}", frame.raw(), e);
            }
        });
    }

    public void disconnected(Channel ch) {
        String mn = sessions.unbind(ch);
        if (mn != null) {
            workers[Math.floorMod(mn.hashCode(), WORKERS)].execute(() -> state.offline(mn, "连接断开"));
        }
    }

    private void handle(Channel ch, Hj212Frame frame) {
        String remote = String.valueOf(ch.remoteAddress()).replace("/", "");
        Hj212Packet p = frame.packet();
        Device device = p == null || p.getMn() == null ? null : devices.findByMn(p.getMn()).orElse(null);
        Long deviceId = device == null ? null : device.getId();
        if (!frame.ok()) {
            // CRC 错误不回 9014，让设备重发
            raw.in(frame.raw(), p, deviceId, remote, frame.error());
            return;
        }
        if (device == null) {
            raw.in(frame.raw(), p, null, remote, "未登记的设备");
            return;
        }
        if (device.getPassword() != null && !device.getPassword().isEmpty() && !device.getPassword().equals(p.getPw())) {
            raw.in(frame.raw(), p, deviceId, remote, "访问密码错误");
            return;
        }
        if (device.isRequireEncryption() && Hj212Crypto.mustEncrypt(p.getCn()) && !p.isEncrypted()) {
            raw.in(frame.raw(), p, deviceId, remote, "设备要求加密传输（HJ212-2025 6.4），这条是明文，不收");
            return;
        }
        raw.in(frame.raw(), p, deviceId, remote, null);
        String version = Hj212Versions.detect(p);
        if (sessions.bind(device.getMn(), ch)) {
            state.connected(deviceId, remote, version);
        } else {
            state.touch(deviceId, remote, version);
        }

        if (Hj212Commands.HEARTBEAT.equals(p.getCn())) {
            // 2025 版心跳包（表 C.54）：回通知应答
            if (p.needAck() && ch.isActive()) {
                Hj212Packet ack = p.reply(Hj212Commands.NOTIFY_ACK);
                ch.writeAndFlush(ack);
                raw.out(ack, deviceId);
            }
            return;
        }
        Hj212Packet data = Hj212Versions.normalize(p, null);
        DataType type = DataType.ofCn(data.getCn());
        if (type != null && device.isEnabled()) {
            List<MonitorData> saved = dataService.save(device, data, type, false);
            // 补传的数据（2025 版 RF=1）不判断预警，避免补数据时刷出一堆旧预警
            if (!saved.isEmpty() && !p.isRefill()) {
                alarmEngine.onData(device, type, saved);
            }
        }
        if (p.needAck() && props.hj212().ackData() && Hj212Commands.isData(p.getCn()) && ch.isActive()) {
            Hj212Packet ack = p.dataAck();
            ch.writeAndFlush(ack);
            raw.out(ack, deviceId);
        }
    }
}
