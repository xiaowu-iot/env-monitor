package io.github.xiaowuiot.envmonitor.ingest;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import io.github.xiaowuiot.envmonitor.config.EnvProperties;
import io.github.xiaowuiot.envmonitor.hj212.codec.Hj212Encoder;
import io.github.xiaowuiot.envmonitor.hj212.codec.Hj212Frame;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * HJ212 TCP 服务端。随 Spring 启停。
 */
@Component
public class Hj212Server implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(Hj212Server.class);

    private final EnvProperties props;
    private final IngestService ingest;
    private EventLoopGroup boss;
    private EventLoopGroup workers;
    private Channel serverChannel;
    private volatile boolean running;

    private final io.github.xiaowuiot.envmonitor.device.SecretKeyService keys;

    public Hj212Server(EnvProperties props, IngestService ingest, io.github.xiaowuiot.envmonitor.device.SecretKeyService keys) {
        this.props = props;
        this.ingest = ingest;
        this.keys = keys;
    }

    @Override
    public void start() {
        if (!props.hj212().enabled()) {
            log.info("HJ212 接入已关闭（env.hj212.enabled=false）");
            return;
        }
        boss = new NioEventLoopGroup(1);
        workers = new NioEventLoopGroup();
        Handler handler = new Handler();
        int idle = props.device().offlineTimeoutSeconds();
        ServerBootstrap b = new ServerBootstrap().group(boss, workers).channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 超过离线时间没有任何数据就断开，设备会自己重连
                        ch.pipeline().addLast(new IdleStateHandler(idle, 0, 0, TimeUnit.SECONDS));
                        // 2025 版加密报文按 MN 找这台设备的密钥解密
                        Hj212Encoder.addCodec(ch.pipeline(), keys);
                        ch.pipeline().addLast(handler);
                    }
                });
        try {
            serverChannel = b.bind(props.hj212().port()).sync().channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HJ212 服务启动被中断", e);
        }
        running = true;
        log.info("HJ212 服务已启动，监听 TCP {}", props.hj212().port());
    }

    @Override
    public void stop() {
        running = false;
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (boss != null) {
            boss.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
            workers.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @ChannelHandler.Sharable
    private class Handler extends SimpleChannelInboundHandler<Hj212Frame> {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            log.debug("新连接 {}", ctx.channel().remoteAddress());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            ingest.disconnected(ctx.channel());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Hj212Frame frame) {
            ingest.submit(ctx.channel(), frame);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                log.info("{} 长时间没有数据，断开连接", ctx.channel().remoteAddress());
                ctx.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("{} 连接异常：{}", ctx.channel().remoteAddress(), cause.getMessage());
            ctx.close();
        }
    }
}
