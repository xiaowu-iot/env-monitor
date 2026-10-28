package io.github.xiaowuiot.envmonitor.device;

/**
 * 设备上下线事件，需要在设备上下线时做事情的（比如离线预警）可以监听它。
 */
public final class DeviceEvents {

    private DeviceEvents() {
    }

    public record Online(Long deviceId, String mn) {
    }

    /**
     * @param reason 连接断开 / 超时未上报 / 停用
     */
    public record Offline(Long deviceId, String mn, String reason) {
    }
}
