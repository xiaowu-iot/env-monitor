package io.github.xiaowuiot.envmonitor.alarm;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.xiaowuiot.envmonitor.data.DataType;
import io.github.xiaowuiot.envmonitor.data.LatestValueRepository;
import io.github.xiaowuiot.envmonitor.data.MonitorData;
import io.github.xiaowuiot.envmonitor.device.Device;
import io.github.xiaowuiot.envmonitor.device.FactorService;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Commands;

/**
 * 超标判断。每收到一包数据，逐个因子、逐条规则判断一遍：超标就产生（或累加）预警，恢复正常就标记已恢复。
 */
@Service
public class AlarmEngine {

    private static final Logger log = LoggerFactory.getLogger(AlarmEngine.class);

    private final AlarmRuleRepository ruleRepo;
    private final AlarmService alarmService;
    private final FactorService factors;
    private final LatestValueRepository latest;

    private volatile List<AlarmRule> rules;

    public AlarmEngine(AlarmRuleRepository ruleRepo, AlarmService alarmService, FactorService factors,
                       LatestValueRepository latest) {
        this.ruleRepo = ruleRepo;
        this.alarmService = alarmService;
        this.factors = factors;
        this.latest = latest;
    }

    /** 规则改了之后调用 */
    public void reloadRules() {
        rules = null;
    }

    private List<AlarmRule> rules() {
        List<AlarmRule> r = rules;
        if (r == null) {
            r = ruleRepo.findByEnabledTrue();
            rules = r;
        }
        return r;
    }

    public void onData(Device device, DataType type, List<MonitorData> records) {
        for (AlarmRule rule : rules()) {
            if (rule.getDataType() != type || !rule.covers(device.getId(), device.getSiteId())) {
                continue;
            }
            for (MonitorData d : records) {
                if (!rule.getFactorCode().equals(d.getFactorCode())) {
                    continue;
                }
                try {
                    evaluate(rule, device, d);
                } catch (RuntimeException e) {
                    log.warn("预警判断出错 规则={} 设备={}：{}", rule.getName(), device.getMn(), e.getMessage(), e);
                }
            }
        }
    }

    private void evaluate(AlarmRule rule, Device device, MonitorData d) {
        Double v = d.mainValue();
        // 停运、维护、故障、校准、通讯异常时的数据不是真实排放，不参与超标判断
        if (v == null || !Hj212Commands.isValidFlag(d.getFlag())) {
            return;
        }
        boolean over = (rule.getUpperLimit() != null && v > rule.getUpperLimit())
                || (rule.getLowerLimit() != null && v < rule.getLowerLimit());
        if (rule.getDataType() == DataType.REALTIME) {
            latest.findByDeviceIdAndFactorCode(device.getId(), d.getFactorCode()).ifPresent(l -> {
                if (l.isOverLimit() != over) {
                    l.setOverLimit(over);
                    latest.save(l);
                }
            });
        }
        String name = factors.name(d.getFactorCode());
        String unit = factors.unit(d.getFactorCode());
        String label = rule.getDataType() == DataType.REALTIME ? "实时值" : rule.getDataType().label();
        if (over) {
            alarmService.raise(rule, device, d.getFactorCode(), v,
                    name + label + " " + fmt(v) + unit + "，超出限值（" + rule.thresholdText() + unit + "）");
        } else {
            alarmService.recover(rule.getId(), device.getId(), d.getFactorCode(), name + "恢复正常（" + fmt(v) + unit + "）");
        }
    }

    private static String fmt(double v) {
        if (v == Math.floor(v) && Math.abs(v) < 1e9) {
            return String.valueOf((long) v);
        }
        return String.valueOf(Math.round(v * 1000) / 1000.0);
    }
}
