package io.github.xiaowuiot.envmonitor.hj212.codec;

import io.github.xiaowuiot.envmonitor.hj212.Hj212Format;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Packet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;

/**
 * {@link Hj212Packet} → 一帧。
 */
@ChannelHandler.Sharable
public class Hj212Encoder extends MessageToByteEncoder<Hj212Packet> {

    public static final Hj212Encoder INSTANCE = new Hj212Encoder();

    /**
     * 往 pipeline 里加上切帧、解码、编码三个处理器
     */
    public static void addCodec(ChannelPipeline pipeline) {
        pipeline.addLast("hj212Frame", new Hj212FrameDecoder())
                .addLast("hj212Decoder", new Hj212Decoder())
                .addLast("hj212Encoder", INSTANCE);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Hj212Packet msg, ByteBuf out) {
        out.writeCharSequence(Hj212Format.frame(Hj212Format.format(msg)), StandardCharsets.US_ASCII);
    }
}
