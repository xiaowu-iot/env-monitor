package io.github.xiaowuiot.envmonitor.data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.xiaowuiot.envmonitor.device.Device;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Factors;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Packet;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Time;

/**
 * 把一包监测数据拆成按因子的记录入库，并更新最新值。
 */
@Service
public class DataService {

    private final MonitorDataRepository data;
    private final LatestValueRepository latest;

    public DataService(MonitorDataRepository data, LatestValueRepository latest) {
        this.data = data;
        this.latest = latest;
    }

    /**
     * @return 入库的记录；重复的统计数据（设备没收到应答重发的）跳过
     */
    @Transactional
    public List<MonitorData> save(Device device, Hj212Packet p, DataType type, boolean requested) {
        LocalDateTime time = Hj212Time.parse(p.getParam("DataTime"));
        if (time == null) {
            time = LocalDateTime.now().withNano(0);
        }
        List<MonitorData> saved = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> f : p.getFactors().entrySet()) {
            String code = f.getKey();
            if (!Hj212Factors.isFactorCode(code)) {
                continue;
            }
            if (type != DataType.REALTIME && data.existsByDeviceIdAndFactorCodeAndDataTypeAndDataTime(device.getId(), code, type, time)) {
                continue;
            }
            Map<String, String> fields = f.getValue();
            MonitorData d = new MonitorData(device.getId(), device.getMn(), type, time, code);
            d.setRtd(num(fields.get("Rtd")));
            d.setAvgVal(num(fields.get("Avg")));
            d.setMinVal(num(fields.get("Min")));
            d.setMaxVal(num(fields.get("Max")));
            d.setCou(num(fields.get("Cou")));
            d.setZsVal(num(fields.containsKey("ZsRtd") ? fields.get("ZsRtd") : fields.get("ZsAvg")));
            d.setFlag(fields.get("Flag"));
            Double rs = num(fields.get("RS"));
            d.setRs(rs == null ? null : rs.intValue());
            d.setRt(num(fields.get("RT")));
            d.setRequested(requested);
            saved.add(data.save(d));
            if (type == DataType.REALTIME && d.getRtd() != null) {
                LocalDateTime t = time;
                LatestValue l = latest.findByDeviceIdAndFactorCode(device.getId(), code)
                        .orElseGet(() -> new LatestValue(device.getId(), code));
                if (l.getDataTime() == null || !t.isBefore(l.getDataTime())) {
                    l.setValue(d.getRtd());
                    l.setFlag(d.getFlag());
                    l.setDataTime(t);
                    l.setUpdatedAt(LocalDateTime.now());
                    latest.save(l);
                }
            }
        }
        return saved;
    }

    private static Double num(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
