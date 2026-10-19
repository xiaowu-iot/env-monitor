package io.github.xiaowuiot.envmonitor.hj212.codec;

import io.github.xiaowuiot.envmonitor.hj212.Hj212Packet;

/**
 * 收到的一帧。CRC 错误或格式错误时 packet 为 null、error 是原因，上层可以记录原始报文、回 9011 告诉对方 CRC 错误。
 *
 * @param raw    原始报文（去掉末尾的 \r\n）
 * @param packet 解析结果
 * @param error  错误原因，正常时为 null
 */
public record Hj212Frame(String raw, Hj212Packet packet, String error) {

    public boolean ok() {
        return error == null;
    }
}
