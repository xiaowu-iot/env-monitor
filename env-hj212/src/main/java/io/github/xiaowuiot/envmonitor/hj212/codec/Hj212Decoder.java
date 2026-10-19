package io.github.xiaowuiot.envmonitor.hj212.codec;

import io.github.xiaowuiot.envmonitor.hj212.Hj212Crc;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Format;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Packet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.github.xiaowuiot.envmonitor.hj212.codec.Hj212FrameDecoder.HEADER_LENGTH;
import static io.github.xiaowuiot.envmonitor.hj212.codec.Hj212FrameDecoder.TRAILER_LENGTH;

/**
 * 一帧 → {@link Hj212Frame}：校验 CRC、解析数据段。出错的帧也往下传，由业务决定怎么记录和应答。
 */
@ChannelHandler.Sharable
public class Hj212Decoder extends MessageToMessageDecoder<ByteBuf> {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf frame, List<Object> out) {
        int start = frame.readerIndex();
        int dataLength = frame.readableBytes() - HEADER_LENGTH - TRAILER_LENGTH;
        String raw = frame.toString(start, frame.readableBytes() - 2, StandardCharsets.US_ASCII);
        String data = frame.toString(start + HEADER_LENGTH, dataLength, StandardCharsets.US_ASCII);
        int expected;
        try {
            expected = Integer.parseInt(frame.toString(start + HEADER_LENGTH + dataLength, 4, StandardCharsets.US_ASCII), 16);
        } catch (NumberFormatException e) {
            out.add(new Hj212Frame(raw, tryParse(data), "CRC 不是十六进制"));
            return;
        }
        int actual = Hj212Crc.crc16(frame, start + HEADER_LENGTH, dataLength);
        if (expected != actual) {
            out.add(new Hj212Frame(raw, tryParse(data),
                    String.format("CRC 校验失败，报文中是 %04X，计算结果是 %04X", expected, actual)));
            return;
        }
        try {
            out.add(new Hj212Frame(raw, Hj212Format.parse(data), null));
        } catch (RuntimeException e) {
            out.add(new Hj212Frame(raw, null, "格式错误：" + e.getMessage()));
        }
    }

    /**
     * CRC 错了也尽量解析出 QN、MN，用来回 9011（QnRtn=9）
     */
    private static Hj212Packet tryParse(String data) {
        try {
            return Hj212Format.parse(data);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
