package io.github.xiaowuiot.envmonitor.sim;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.xiaowuiot.envmonitor.device.Device;
import io.github.xiaowuiot.envmonitor.device.DeviceRepository;
import io.github.xiaowuiot.envmonitor.simulator.Scenario;
import io.github.xiaowuiot.envmonitor.simulator.SimStatus;

@RestController
@RequestMapping("/api/simulator")
@PreAuthorize("hasAuthority('PERM_SIMULATOR')")
public class SimulatorController {

    private final SimulatorService sim;
    private final DeviceRepository devices;

    public SimulatorController(SimulatorService sim, DeviceRepository devices) {
        this.sim = sim;
        this.devices = devices;
    }

    public record Row(Long deviceId, String mn, String name, List<String> factorCodes, boolean deviceOnline,
                      SimStatus sim) {
    }

    public record StartRequest(List<Long> deviceIds, Scenario scenario) {
    }

    public record ScenarioRequest(String mn, Scenario scenario, String factor) {
    }

    @GetMapping
    public List<Row> list() {
        Map<String, SimStatus> st = sim.statuses().stream().collect(Collectors.toMap(SimStatus::mn, Function.identity()));
        return devices.findAllByOrderByIdAsc().stream()
                .map(d -> new Row(d.getId(), d.getMn(), d.getName(), d.getFactorCodes(), d.isOnline(), st.get(d.getMn())))
                .toList();
    }

    @GetMapping("/scenarios")
    public List<Map<String, String>> scenarios() {
        return Arrays.stream(Scenario.values()).map(s -> Map.of("value", s.name(), "label", s.label())).toList();
    }

    /** deviceIds 为空表示全部启用的设备 */
    @PostMapping("/start")
    public Map<String, Integer> start(@RequestBody StartRequest body) {
        List<Device> list = body.deviceIds() == null || body.deviceIds().isEmpty()
                ? devices.findAllByOrderByIdAsc().stream().filter(Device::isEnabled).toList()
                : devices.findAllById(body.deviceIds());
        list.forEach(d -> sim.start(d, body.scenario()));
        return Map.of("started", list.size());
    }

    @PostMapping("/stop")
    public Map<String, Boolean> stop(@RequestBody StartRequest body) {
        if (body.deviceIds() == null || body.deviceIds().isEmpty()) {
            sim.stopAll();
        } else {
            devices.findAllById(body.deviceIds()).forEach(d -> sim.stop(d.getMn()));
        }
        return Map.of("ok", true);
    }

    @PostMapping("/scenario")
    public Map<String, Boolean> scenario(@RequestBody ScenarioRequest body) {
        sim.scenario(body.mn(), body.scenario(), body.factor());
        return Map.of("ok", true);
    }
}
