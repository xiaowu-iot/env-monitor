package io.github.xiaowuiot.envmonitor.hj212.codec;

import io.github.xiaowuiot.envmonitor.hj212.Hj212Format;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Packet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * {@link Hj212Packet} → 一帧。
 * 包上设了密钥（{@link Hj212Packet#encryptWith(byte[])}）的按 2025 版加密 CP。
 */
@ChannelHandler.Sharable
public class Hj212Encoder extends MessageToByteEncoder<Hj212Packet> {

    public static final Hj212Encoder INSTANCE = new Hj212Encoder();

    /**
     * 往 pipeline 里加上切帧、解码、编码三个处理器（不处理加密）
     */
    public static void addCodec(ChannelPipeline pipeline) {
        addCodec(pipeline, null);
    }

    /**
     * 往 pipeline 里加上切帧、解码、编码三个处理器
     *
     * @param keys 按 MN 查 2025 版的通信密钥，用来解密收到的报文；null 表示不解密
     */
    public static void addCodec(ChannelPipeline pipeline, Hj212Decoder.KeyLookup keys) {
        pipeline.addLast("hj212Frame", new Hj212FrameDecoder())
                .addLast("hj212Decoder", new Hj212Decoder(keys))
                .addLast("hj212Encoder", INSTANCE);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Hj212Packet msg, ByteBuf out) {
        out.writeBytes(Hj212Format.frameBytes(Hj212Format.format(msg), msg.getCipherKey()));
    }
}
