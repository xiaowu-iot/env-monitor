package io.github.xiaowuiot.envmonitor.simulator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 模拟设备的运行状态，给页面展示用。
 */
public record SimStatus(String mn, boolean running, boolean connected, Scenario scenario, String scenarioFactor,
                        int rtdInterval, int minuteInterval, long sent, long acked, LocalDateTime lastSentAt,
                        Map<String, Double> lastValues, List<String> logs) {
}
