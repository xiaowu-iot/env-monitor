package io.github.xiaowuiot.envmonitor.hj212.codec;

import io.github.xiaowuiot.envmonitor.hj212.Hj212Crc;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Crypto;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Format;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Packet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.github.xiaowuiot.envmonitor.hj212.codec.Hj212FrameDecoder.HEADER_LENGTH;
import static io.github.xiaowuiot.envmonitor.hj212.codec.Hj212FrameDecoder.TRAILER_LENGTH;

/**
 * 一帧 → {@link Hj212Frame}：校验 CRC、解析数据段。出错的帧也往下传，由业务决定怎么记录和应答。
 * <p>
 * 2025 版加密传输时 CRC 是按明文算的：先按收到的字节校验，对不上再用这台设备的密钥解密 CP 后重新校验，
 * 对上了就是加密报文（{@link Hj212Packet#isEncrypted()}）。密钥更新的过渡期新旧密钥都试一遍。
 */
@ChannelHandler.Sharable
public class Hj212Decoder extends MessageToMessageDecoder<ByteBuf> {

    /**
     * 按 MN 查通信密钥，可以返回多个（当前密钥、上一个密钥），没有返回空列表
     */
    @FunctionalInterface
    public interface KeyLookup {
        List<byte[]> keys(String mn);
    }

    private final KeyLookup keys;

    public Hj212Decoder() {
        this(null);
    }

    public Hj212Decoder(KeyLookup keys) {
        this.keys = keys;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf frame, List<Object> out) {
        int start = frame.readerIndex();
        int dataLength = frame.readableBytes() - HEADER_LENGTH - TRAILER_LENGTH;
        byte[] data = ByteBufUtil.getBytes(frame, start + HEADER_LENGTH, dataLength);
        String head = frame.toString(start, HEADER_LENGTH, StandardCharsets.US_ASCII);
        String crcText = frame.toString(start + HEADER_LENGTH + dataLength, 4, StandardCharsets.US_ASCII);
        String text = new String(data, StandardCharsets.UTF_8);
        int expected;
        try {
            expected = Integer.parseInt(crcText, 16);
        } catch (NumberFormatException e) {
            out.add(new Hj212Frame(head + text + crcText, tryParse(text), "CRC 不是十六进制"));
            return;
        }
        int actual = Hj212Crc.crc16(data);
        if (expected == actual) {
            out.add(parse(head + text + crcText, text, false));
            return;
        }
        // 可能是加密报文：用 MN 找密钥，解密后再校验
        Hj212Packet header = tryParseHeader(text);
        if (keys != null && header != null && header.getMn() != null && !header.getMn().isEmpty()) {
            for (byte[] key : keys.keys(header.getMn())) {
                try {
                    byte[] plain = Hj212Crypto.decrypt(data, key);
                    if (Hj212Crc.crc16(plain) == expected) {
                        String plainText = new String(plain, StandardCharsets.UTF_8);
                        out.add(parse(head + plainText + crcText, plainText, true));
                        return;
                    }
                } catch (RuntimeException ignored) {
                    // 这把密钥不对，试下一把
                }
            }
        }
        out.add(new Hj212Frame(head + text + crcText, header,
                String.format("CRC 校验失败，报文中是 %04X，计算结果是 %04X", expected, actual)));
    }

    private static Hj212Frame parse(String raw, String data, boolean encrypted) {
        try {
            Hj212Packet p = Hj212Format.parse(data);
            if (encrypted) {
                p.markEncrypted();
            }
            return new Hj212Frame(raw, p, null);
        } catch (RuntimeException e) {
            return new Hj212Frame(raw, null, "格式错误：" + e.getMessage());
        }
    }

    /**
     * CRC 错了也尽量解析出 QN、MN，用来回 9011（QnRtn=9）
     */
    private static Hj212Packet tryParse(String data) {
        try {
            return Hj212Format.parse(data);
        } catch (RuntimeException e) {
            return tryParseHeader(data);
        }
    }

    /**
     * 只解析 CP 前面的包头（加密报文的 CP 是密文，整段解析不了）
     */
    private static Hj212Packet tryParseHeader(String data) {
        int cp = data.indexOf("CP=&&");
        if (cp < 0) {
            return null;
        }
        try {
            return Hj212Format.parse(data.substring(0, cp) + "CP=&&&&");
        } catch (RuntimeException e) {
            return null;
        }
    }
}
