package io.github.xiaowuiot.envmonitor.data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.xiaowuiot.envmonitor.common.ApiException;
import io.github.xiaowuiot.envmonitor.device.Device;
import io.github.xiaowuiot.envmonitor.device.DeviceController;
import io.github.xiaowuiot.envmonitor.device.DeviceRepository;
import io.github.xiaowuiot.envmonitor.security.CurrentUser;

/**
 * 实时数据和历史曲线。
 */
@RestController
@RequestMapping("/api/data")
public class DataController {

    private final DataQueryService query;
    private final DeviceController devices;
    private final DeviceRepository deviceRepo;
    private final CurrentUser current;

    public DataController(DataQueryService query, DeviceController devices, DeviceRepository deviceRepo, CurrentUser current) {
        this.deviceRepo = deviceRepo;
        this.query = query;
        this.devices = devices;
        this.current = current;
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAuthority('PERM_DEVICE_VIEW')")
    public List<DataQueryService.DeviceLatest> latest(@RequestParam(name = "siteId", required = false) Long siteId) {
        current.get();
        List<Device> list = deviceRepo.findAllByOrderByIdAsc().stream()
                .filter(d -> siteId == null || siteId.equals(d.getSiteId())).toList();
        return query.latest(list);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('PERM_DEVICE_VIEW')")
    public DataQueryService.Series history(@RequestParam("deviceId") Long deviceId,
                                           @RequestParam("factor") String factor,
                                           @RequestParam(name = "type", defaultValue = "REALTIME") DataType type,
                                           @RequestParam(name = "from", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
                                           @RequestParam(name = "to", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to) {
        Device d = devices.check(deviceId);
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime start = from == null ? end.minusHours(type == DataType.REALTIME || type == DataType.MINUTE ? 6 : 24 * 7) : from;
        if (start.isAfter(end)) {
            throw ApiException.badRequest("开始时间不能晚于结束时间");
        }
        return query.series(d, factor, type, start, end);
    }

    /**
     * 一台设备在一段时间内的多因子表格（按时间一行）
     */
    @GetMapping("/table")
    @PreAuthorize("hasAuthority('PERM_DEVICE_VIEW')")
    public List<Map<String, Object>> table(@RequestParam("deviceId") Long deviceId,
                                           @RequestParam(name = "type", defaultValue = "MINUTE") DataType type,
                                           @RequestParam(name = "from", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
                                           @RequestParam(name = "to", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to) {
        Device d = devices.check(deviceId);
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime start = from == null ? end.minusHours(2) : from;
        return query.table(d, type, start, end);
    }

    /** 数据类型下拉框 */
    @GetMapping("/types")
    public List<Map<String, String>> types() {
        List<Map<String, String>> list = new ArrayList<>();
        for (DataType t : DataType.values()) {
            list.add(Map.of("value", t.name(), "label", t.label()));
        }
        return list;
    }

}
