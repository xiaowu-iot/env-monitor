package io.github.xiaowuiot.envmonitor.simulator;

import io.github.xiaowuiot.envmonitor.hj212.Hj212Commands;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Crypto;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Packet;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Time;
import io.github.xiaowuiot.envmonitor.hj212.codec.Hj212Encoder;
import io.github.xiaowuiot.envmonitor.hj212.codec.Hj212Frame;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 一台模拟数采仪：连上平台后按实时数据间隔上报 2011，按分钟数据间隔上报 2051，断线 5 秒后自动重连。
 * 上报都带"需要应答"标志，收到平台的 9014 计入应答数。
 * <p>
 * 所有状态只在本设备绑定的 EventLoop 线程里修改，不用加锁。
 */
public class SimDevice {

    private static final Logger log = LoggerFactory.getLogger(SimDevice.class);
    private static final int MAX_LOGS = 60;

    private final SimDeviceConfig config;
    private final EventLoop loop;
    private final Random random = new Random();

    private Channel channel;
    private volatile boolean running;
    private volatile boolean connected;
    private ScheduledFuture<?> rtdTask;
    private ScheduledFuture<?> minuteTask;
    private ScheduledFuture<?> heartbeatTask;

    private volatile long sent;
    private volatile long acked;
    private volatile LocalDateTime lastSentAt;

    private final Map<String, Double> lastValues = new LinkedHashMap<>();
    private final Map<String, List<Double>> minuteBuffer = new LinkedHashMap<>();
    private final Deque<String> logs = new ArrayDeque<>();

    SimDevice(SimDeviceConfig config, EventLoop loop) {
        this.config = config;
        this.loop = loop;
    }

    void start() {
        running = true;
        loop.execute(this::connect);
    }

    void stop() {
        running = false;
        loop.execute(() -> {
            cancelTasks();
            if (channel != null) {
                channel.close();
            }
        });
    }

    public void setScenario(Scenario scenario, String factor) {
        loop.execute(() -> {
            config.setScenario(scenario).setScenarioFactor(factor == null || factor.isBlank() ? null : factor);
            addLog("切换场景：" + scenario.label() + (factor == null ? "" : "（" + factor + "）"));
        });
    }

    private void connect() {
        if (!running) {
            return;
        }
        Bootstrap b = new Bootstrap().group(loop).channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        Hj212Encoder.addCodec(ch.pipeline());
                        ch.pipeline().addLast(new Handler());
                    }
                });
        ChannelFuture f = b.connect(config.getHost(), config.getPort());
        f.addListener(r -> {
            if (!r.isSuccess()) {
                addLog("连接平台失败：" + r.cause().getMessage() + "，5 秒后重试");
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (running) {
            loop.schedule(this::connect, 5, TimeUnit.SECONDS);
        }
    }

    private void onConnected(Channel ch) {
        channel = ch;
        connected = true;
        addLog("已连接平台 " + config.getHost() + ":" + config.getPort() + (config.isV2025() ? "（HJ212-2025）" : ""));
        if (config.isV2025() && config.getHeartbeatSeconds() > 0) {
            // 2025 版心跳包（表 C.54）
            heartbeatTask = loop.scheduleAtFixedRate(() -> send(create(Hj212Commands.HEARTBEAT)), config.getHeartbeatSeconds(),
                    config.getHeartbeatSeconds(), TimeUnit.SECONDS);
        }
        int rtd = Math.max(1, config.getRtdIntervalSeconds());
        int minute = Math.max(1, config.getMinuteIntervalSeconds());
        rtdTask = loop.scheduleAtFixedRate(this::uploadRealtime, 1, rtd, TimeUnit.SECONDS);
        minuteTask = loop.scheduleAtFixedRate(this::uploadMinute, minute, minute, TimeUnit.SECONDS);
    }

    private void onDisconnected() {
        connected = false;
        channel = null;
        cancelTasks();
        addLog("与平台断开");
        scheduleReconnect();
    }

    private void cancelTasks() {
        if (rtdTask != null) {
            rtdTask.cancel(false);
        }
        if (minuteTask != null) {
            minuteTask.cancel(false);
        }
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        rtdTask = minuteTask = heartbeatTask = null;
    }

    private void uploadRealtime() {
        if (!connected) {
            return;
        }
        Hj212Packet p = create(Hj212Commands.REALTIME)
                .putParam("DataTime", Hj212Time.format(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)));
        for (SimDeviceConfig.Factor f : config.getFactors()) {
            double v = nextValue(f);
            minuteBuffer.computeIfAbsent(f.code(), k -> new ArrayList<>()).add(v);
            p.putFactor(f.code(), "Rtd", fmt(v, f.decimals())).putFactor(f.code(), "Flag", "N");
        }
        send(p);
    }

    private void uploadMinute() {
        if (!connected || minuteBuffer.isEmpty()) {
            return;
        }
        Hj212Packet p = create(Hj212Commands.MINUTE)
                .putParam("DataTime", Hj212Time.format(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)));
        for (SimDeviceConfig.Factor f : config.getFactors()) {
            List<Double> values = minuteBuffer.get(f.code());
            if (values == null || values.isEmpty()) {
                continue;
            }
            double sum = 0;
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (double v : values) {
                sum += v;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            p.putFactor(f.code(), "Avg", fmt(sum / values.size(), f.decimals()))
                    .putFactor(f.code(), "Min", fmt(min, f.decimals()))
                    .putFactor(f.code(), "Max", fmt(max, f.decimals()))
                    .putFactor(f.code(), "Flag", "N");
        }
        minuteBuffer.clear();
        send(p);
    }

    private double nextValue(SimDeviceConfig.Factor f) {
        double v = f.base() + (random.nextDouble() * 2 - 1) * f.amplitude();
        String target = config.getScenarioFactor() == null ? config.getFactors().get(0).code() : config.getScenarioFactor();
        if (config.getScenario() == Scenario.OVER_LIMIT && f.code().equals(target)) {
            v = f.base() * 3 + random.nextDouble() * f.amplitude();
        }
        double scale = Math.pow(10, f.decimals());
        v = Math.max(0, Math.round(v * scale) / scale);
        lastValues.put(f.code(), v);
        return v;
    }

    /**
     * 按配置的版本组包，要求应答：2017 版 Flag=5，2025 版 Flag=9
     */
    private Hj212Packet create(String cn) {
        return Hj212Packet.create(config.isV2025() ? Hj212Packet.VERSION_2025 : Hj212Packet.VERSION_2017, config.getSt(), cn,
                config.getPassword(), config.getMn(), true);
    }

    private void send(Hj212Packet p) {
        if (channel == null || !channel.isActive()) {
            return;
        }
        if (config.isV2025() && config.getSecretKey() != null && Hj212Crypto.mustEncrypt(p.getCn())) {
            // 2025 版 6.4.4：数据命令加密上传
            p.encryptWith(Hj212Crypto.key(config.getSecretKey()));
        }
        channel.writeAndFlush(p);
        sent++;
        lastSentAt = LocalDateTime.now();
    }

    private static String fmt(double v, int decimals) {
        return String.format("%." + decimals + "f", v);
    }

    private void addLog(String line) {
        String entry = LocalDateTime.now().toLocalTime().truncatedTo(ChronoUnit.SECONDS) + " " + line;
        synchronized (logs) {
            logs.addFirst(entry);
            while (logs.size() > MAX_LOGS) {
                logs.removeLast();
            }
        }
        log.debug("[{}] {}", config.getMn(), line);
    }

    public SimStatus status() {
        List<String> copy;
        synchronized (logs) {
            copy = new ArrayList<>(logs);
        }
        return new SimStatus(config.getMn(), running, connected, config.getScenario(), config.getScenarioFactor(),
                config.getRtdIntervalSeconds(), config.getMinuteIntervalSeconds(), sent, acked, lastSentAt,
                new LinkedHashMap<>(lastValues), copy);
    }

    public String mn() {
        return config.getMn();
    }

    private class Handler extends SimpleChannelInboundHandler<Hj212Frame> {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            onConnected(ctx.channel());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            onDisconnected();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Hj212Frame frame) {
            if (frame.ok() && Hj212Commands.DATA_ACK.equals(frame.packet().getCn())) {
                acked++;
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            addLog("连接异常：" + cause.getMessage());
            ctx.close();
        }
    }
}
