package io.github.xiaowuiot.envmonitor.data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.github.xiaowuiot.envmonitor.alarm.AlarmRule;
import io.github.xiaowuiot.envmonitor.alarm.AlarmRuleRepository;
import io.github.xiaowuiot.envmonitor.alarm.AlarmType;
import io.github.xiaowuiot.envmonitor.device.Device;
import io.github.xiaowuiot.envmonitor.device.FactorService;
import io.github.xiaowuiot.envmonitor.site.Site;
import io.github.xiaowuiot.envmonitor.site.SiteRepository;

/**
 * 数据查询，页面和智能助手共用。
 */
@Service
public class DataQueryService {

    /** 数据点太多时抽稀，页面画图够用就行 */
    private static final int MAX_POINTS = 600;

    private final MonitorDataRepository data;
    private final LatestValueRepository latest;
    private final FactorService factors;
    private final SiteRepository sites;
    private final AlarmRuleRepository rules;

    public DataQueryService(MonitorDataRepository data, LatestValueRepository latest, FactorService factors,
                            SiteRepository sites, AlarmRuleRepository rules) {
        this.data = data;
        this.latest = latest;
        this.factors = factors;
        this.sites = sites;
        this.rules = rules;
    }

    public record Value(String code, String name, String unit, Double value, String flag, LocalDateTime dataTime,
                        boolean overLimit) {
    }

    public record DeviceLatest(Long deviceId, String mn, String name, Long siteId, String siteName, boolean online,
                               LocalDateTime lastSeenAt, List<Value> values) {
    }

    public record Point(LocalDateTime t, Double v, Double min, Double max, String flag) {
    }

    public record Series(Long deviceId, String deviceName, String factor, String factorName, String unit,
                         String type, Double upper, Double lower, List<Point> points, Double avg, Double min,
                         Double max, int count, int overCount) {
    }

    public List<DeviceLatest> latest(List<Device> devices) {
        Map<Long, List<LatestValue>> byDevice = latest.findByDeviceIdIn(devices.stream().map(Device::getId).toList())
                .stream().collect(Collectors.groupingBy(LatestValue::getDeviceId));
        Map<Long, String> siteNames = sites.findAll().stream().collect(Collectors.toMap(Site::getId, Site::getName));
        List<DeviceLatest> result = new ArrayList<>();
        for (Device d : devices) {
            Map<String, LatestValue> values = byDevice.getOrDefault(d.getId(), List.of()).stream()
                    .collect(Collectors.toMap(LatestValue::getFactorCode, v -> v, (a, b) -> a));
            List<Value> list = new ArrayList<>();
            for (String code : d.getFactorCodes()) {
                LatestValue v = values.remove(code);
                list.add(toValue(code, v));
            }
            values.forEach((code, v) -> list.add(toValue(code, v)));
            result.add(new DeviceLatest(d.getId(), d.getMn(), d.getName(), d.getSiteId(), siteNames.get(d.getSiteId()),
                    d.isOnline(), d.getLastSeenAt(), list));
        }
        return result;
    }

    private Value toValue(String code, LatestValue v) {
        return new Value(code, factors.name(code), factors.unit(code), v == null ? null : v.getValue(),
                v == null ? null : v.getFlag(), v == null ? null : v.getDataTime(), v != null && v.isOverLimit());
    }

    public Series series(Device d, String factor, DataType type, LocalDateTime from, LocalDateTime to) {
        List<MonitorData> rows = data.findByDeviceIdAndFactorCodeAndDataTypeAndDataTimeBetweenOrderByDataTimeAsc(
                d.getId(), factor, type, from, to);
        AlarmRule limit = rules.findByEnabledTrue().stream()
                .filter(r -> r.getType() == AlarmType.OVER_LIMIT && factor.equals(r.getFactorCode())
                        && r.covers(d.getId(), d.getSiteId()))
                .findFirst().orElse(null);
        Double upper = limit == null ? null : limit.getUpperLimit();
        Double lower = limit == null ? null : limit.getLowerLimit();
        double sum = 0;
        Double min = null;
        Double max = null;
        int count = 0;
        int over = 0;
        List<Point> points = new ArrayList<>();
        int step = Math.max(1, rows.size() / MAX_POINTS);
        for (int i = 0; i < rows.size(); i++) {
            MonitorData r = rows.get(i);
            Double v = r.mainValue();
            if (v != null) {
                sum += v;
                count++;
                min = min == null ? v : Math.min(min, v);
                max = max == null ? v : Math.max(max, v);
                if ((upper != null && v > upper) || (lower != null && v < lower)) {
                    over++;
                }
            }
            if (i % step == 0 || i == rows.size() - 1) {
                points.add(new Point(r.getDataTime(), v, r.getMinVal(), r.getMaxVal(), r.getFlag()));
            }
        }
        return new Series(d.getId(), d.getName(), factor, factors.name(factor), factors.unit(factor), type.name(),
                upper, lower, points, count == 0 ? null : Math.round(sum / count * 1000) / 1000.0, min, max, count, over);
    }

    public List<Map<String, Object>> table(Device d, DataType type, LocalDateTime from, LocalDateTime to) {
        TreeMap<LocalDateTime, Map<String, Object>> rows = new TreeMap<>((a, b) -> b.compareTo(a));
        for (MonitorData r : data.findByDeviceIdAndDataTypeAndDataTimeBetweenOrderByDataTimeAsc(d.getId(), type, from, to)) {
            Map<String, Object> row = rows.computeIfAbsent(r.getDataTime(), t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("time", t);
                return m;
            });
            row.put(r.getFactorCode(), r.mainValue());
            if (r.getFlag() != null && !"N".equals(r.getFlag())) {
                row.put(r.getFactorCode() + "_flag", r.getFlag());
            }
        }
        return rows.values().stream().limit(500).toList();
    }
}
