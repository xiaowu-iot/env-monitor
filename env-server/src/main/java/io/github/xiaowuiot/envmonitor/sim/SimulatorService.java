package io.github.xiaowuiot.envmonitor.sim;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import io.github.xiaowuiot.envmonitor.common.ApiException;
import io.github.xiaowuiot.envmonitor.config.EnvProperties;
import io.github.xiaowuiot.envmonitor.device.Device;
import io.github.xiaowuiot.envmonitor.device.DeviceRepository;
import io.github.xiaowuiot.envmonitor.simulator.Scenario;
import io.github.xiaowuiot.envmonitor.simulator.SimDevice;
import io.github.xiaowuiot.envmonitor.simulator.SimDeviceConfig;
import io.github.xiaowuiot.envmonitor.simulator.SimStatus;
import io.github.xiaowuiot.envmonitor.simulator.Simulator;
import jakarta.annotation.PreDestroy;

/**
 * 内置模拟器：按平台里登记的设备（MN、密码、因子）生成模拟数采仪，连本机的 HJ212 端口。
 * 走的是真实的 TCP 连接和报文，平台这边没有任何特殊处理。
 */
@Service
public class SimulatorService {

    private static final Logger log = LoggerFactory.getLogger(SimulatorService.class);

    /** 常见因子的正常值、波动范围、小数位，模拟器按这个生成数据 */
    private static final Map<String, double[]> PROFILES = Map.ofEntries(
            Map.entry("w01018", new double[]{28, 6, 1}),
            Map.entry("w21003", new double[]{1.2, 0.4, 2}),
            Map.entry("w01001", new double[]{7.2, 0.3, 2}),
            Map.entry("w21011", new double[]{0.25, 0.08, 3}),
            Map.entry("w21001", new double[]{9, 2, 2}),
            Map.entry("w00000", new double[]{110, 25, 1}),
            Map.entry("a21026", new double[]{18, 5, 1}),
            Map.entry("a21002", new double[]{30, 8, 1}),
            Map.entry("a34013", new double[]{5, 1.5, 1}),
            Map.entry("a19001", new double[]{10, 1, 1}),
            Map.entry("a34004", new double[]{35, 10, 0}),
            Map.entry("a34002", new double[]{60, 15, 0}));

    private final DeviceRepository devices;
    private final EnvProperties props;
    private final Simulator simulator = new Simulator(2);

    private final io.github.xiaowuiot.envmonitor.device.SecretKeyService keys;

    public SimulatorService(DeviceRepository devices, EnvProperties props, io.github.xiaowuiot.envmonitor.device.SecretKeyService keys) {
        this.devices = devices;
        this.props = props;
        this.keys = keys;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void autoStart() {
        if (props.simulator().autoStart()) {
            devices.findAllByOrderByIdAsc().stream().filter(Device::isEnabled).forEach(d -> start(d, Scenario.NORMAL));
            log.info("已自动启动内置模拟设备");
        }
    }

    @PreDestroy
    void shutdown() {
        simulator.close();
    }

    public SimDevice start(Device d, Scenario scenario) {
        SimDeviceConfig c = new SimDeviceConfig().setHost("127.0.0.1").setPort(props.hj212().port())
                .setMn(d.getMn()).setPassword(d.getPassword() == null ? "" : d.getPassword())
                .setSt(d.getSt() == null ? "32" : d.getSt())
                .setRtdIntervalSeconds(props.simulator().rtdIntervalSeconds())
                .setMinuteIntervalSeconds(props.simulator().minuteIntervalSeconds())
                .setScenario(scenario == null ? Scenario.NORMAL : scenario)
                // 2025 版设备按平台上登记的密钥加密上报，模拟"数采仪里固化的密钥"
                .setProtocolVersion(d.getProtocolVersion())
                .setSecretKey(d.getSecretKey() == null ? null : keys.open(d.getSecretKey()));
        List<String> codes = d.getFactorCodes().isEmpty() ? List.of("w01018", "w21003") : d.getFactorCodes();
        for (String code : codes) {
            double[] p = PROFILES.getOrDefault(code, new double[]{10, 2, 2});
            c.addFactor(new SimDeviceConfig.Factor(code, p[0], p[1], (int) p[2]));
        }
        return simulator.start(c);
    }

    public void stop(String mn) {
        simulator.stop(mn);
    }

    public void stopAll() {
        simulator.stopAll();
    }

    public void scenario(String mn, Scenario scenario, String factor) {
        SimDevice d = simulator.get(mn);
        if (d == null) {
            throw ApiException.badRequest("模拟设备 " + mn + " 没有运行，请先启动");
        }
        d.setScenario(scenario, factor);
    }

    public SimDevice get(String mn) {
        return simulator.get(mn);
    }

    public List<SimStatus> statuses() {
        return simulator.statuses();
    }
}
