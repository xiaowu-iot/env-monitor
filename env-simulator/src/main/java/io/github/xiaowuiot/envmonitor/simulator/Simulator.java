package io.github.xiaowuiot.envmonitor.simulator;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟器：管理一批模拟数采仪。命令行和平台后台的"设备模拟器"页面共用这个类。
 */
public class Simulator implements AutoCloseable {

    private final EventLoopGroup group;
    private final Map<String, SimDevice> devices = new ConcurrentHashMap<>();

    public Simulator() {
        this(Math.max(2, Runtime.getRuntime().availableProcessors()));
    }

    public Simulator(int threads) {
        this.group = new NioEventLoopGroup(threads);
    }

    /**
     * 启动一台模拟设备。同一个 MN 已经在运行的，先停掉再按新配置启动
     */
    public SimDevice start(SimDeviceConfig config) {
        stop(config.getMn());
        SimDevice d = new SimDevice(config, group.next());
        devices.put(config.getMn(), d);
        d.start();
        return d;
    }

    public void stop(String mn) {
        SimDevice d = devices.remove(mn);
        if (d != null) {
            d.stop();
        }
    }

    public void stopAll() {
        new ArrayList<>(devices.keySet()).forEach(this::stop);
    }

    public SimDevice get(String mn) {
        return devices.get(mn);
    }

    public List<SimStatus> statuses() {
        return devices.values().stream().map(SimDevice::status)
                .sorted(Comparator.comparing(SimStatus::mn)).toList();
    }

    @Override
    public void close() {
        stopAll();
        group.shutdownGracefully();
    }
}
