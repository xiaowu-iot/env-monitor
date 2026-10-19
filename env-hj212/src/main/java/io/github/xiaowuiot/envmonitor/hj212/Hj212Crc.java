package io.github.xiaowuiot.envmonitor.hj212;

import io.netty.buffer.ByteBuf;

/**
 * HJ212-2017 附录中给出的 CRC16 算法。
 * <p>
 * 和 CRC-16/MODBUS 只差一处：每个字节是和 {@code crc >> 8} 异或，而不是和 crc 本身异或。
 * 直接拿 MODBUS 的 CRC 去算 212 报文，结果怎么都对不上，问题就出在这里。
 * <p>
 * 校验范围是数据段（不含 {@code ##} 和 4 位长度），结果以 4 位大写十六进制跟在数据段后面。
 */
public final class Hj212Crc {

    private Hj212Crc() {
    }

    public static int crc16(ByteBuf buf, int index, int length) {
        int crc = 0xFFFF;
        for (int i = index; i < index + length; i++) {
            crc = (crc >>> 8) ^ (buf.getByte(i) & 0xFF);
            crc = shift8(crc);
        }
        return crc;
    }

    public static int crc16(byte[] bytes) {
        int crc = 0xFFFF;
        for (byte b : bytes) {
            crc = (crc >>> 8) ^ (b & 0xFF);
            crc = shift8(crc);
        }
        return crc;
    }

    private static int shift8(int crc) {
        for (int j = 0; j < 8; j++) {
            if ((crc & 1) != 0) {
                crc = (crc >>> 1) ^ 0xA001;
            } else {
                crc >>>= 1;
            }
        }
        return crc;
    }
}
