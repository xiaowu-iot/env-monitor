package io.github.xiaowuiot.envmonitor.hj212.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 按 {@code ## + 4 位长度 + 数据段 + 4 位 CRC + \r\n} 切帧，解决 TCP 的半包和粘包。
 * <p>
 * 输出完整的一帧（含 ## 和 \r\n），CRC 和数据段交给 {@link Hj212Decoder}。
 * 遇到垃圾数据、长度不对、包尾对不上时只丢弃 1 个字节后重新找 ##，不抛异常：
 * 抛异常会打断本次解码循环，同一次读到的后面几帧要等下次收到数据才会被处理。
 */
public class Hj212FrameDecoder extends ByteToMessageDecoder {

    private static final Logger log = LoggerFactory.getLogger(Hj212FrameDecoder.class);

    /**
     * 长度字段只有 4 位十进制，数据段最多 9999 字节
     */
    public static final int MAX_DATA_LENGTH = 9999;
    static final int HEADER_LENGTH = 6;
    static final int TRAILER_LENGTH = 6;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (in.readableBytes() > 0) {
            int start = indexOfHeader(in);
            if (start < 0) {
                // 最后一个字节可能是被拆开的 ## 的前一半，先留着
                int keep = in.getByte(in.writerIndex() - 1) == '#' ? 1 : 0;
                skip(ctx, in, in.readableBytes() - keep, "找不到包头");
                return;
            }
            if (start > in.readerIndex()) {
                skip(ctx, in, start - in.readerIndex(), "包头前的无效数据");
            }
            if (in.readableBytes() < HEADER_LENGTH) {
                return;
            }
            int length = parseLength(in, in.readerIndex() + 2);
            if (length < 0) {
                skip(ctx, in, 1, "长度不是 4 位数字");
                continue;
            }
            int frameLength = HEADER_LENGTH + length + TRAILER_LENGTH;
            if (in.readableBytes() < frameLength) {
                return;
            }
            int end = in.readerIndex() + frameLength;
            if (in.getByte(end - 2) != '\r' || in.getByte(end - 1) != '\n') {
                skip(ctx, in, 1, "包尾不是 \\r\\n");
                continue;
            }
            out.add(in.readRetainedSlice(frameLength));
        }
    }

    private static int indexOfHeader(ByteBuf in) {
        for (int i = in.readerIndex(); i < in.writerIndex() - 1; i++) {
            if (in.getByte(i) == '#' && in.getByte(i + 1) == '#') {
                return i;
            }
        }
        return -1;
    }

    private static int parseLength(ByteBuf in, int index) {
        int v = 0;
        for (int i = 0; i < 4; i++) {
            byte b = in.getByte(index + i);
            if (b < '0' || b > '9') {
                return -1;
            }
            v = v * 10 + (b - '0');
        }
        return v;
    }

    private void skip(ChannelHandlerContext ctx, ByteBuf in, int length, String reason) {
        if (length <= 0) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("{} 丢弃 {} 字节（{}）：{}", ctx.channel(), length, reason,
                    in.toString(in.readerIndex(), Math.min(length, 128), StandardCharsets.US_ASCII));
        }
        in.skipBytes(length);
    }
}
