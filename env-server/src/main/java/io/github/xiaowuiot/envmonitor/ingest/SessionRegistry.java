package io.github.xiaowuiot.envmonitor.ingest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.xiaowuiot.envmonitor.hj212.Hj212Packet;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

/**
 * MN → TCP 连接。下发命令时按 MN 找连接。
 * <p>
 * 212 没有注册、鉴权这类握手，连接上来的第一条报文里带了 MN，才知道是哪台设备。
 * 同一个 MN 从新连接上来（设备重启、网络切换），旧连接直接关掉，以新连接为准。
 */
@Component
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);
    public static final AttributeKey<String> MN = AttributeKey.valueOf("mn");

    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    /**
     * @return true 表示这是一个新连接（之前没绑定过）
     */
    public boolean bind(String mn, Channel ch) {
        String bound = ch.attr(MN).get();
        if (mn.equals(bound) && channels.get(mn) == ch) {
            return false;
        }
        ch.attr(MN).set(mn);
        Channel old = channels.put(mn, ch);
        if (old != null && old != ch && old.isActive()) {
            log.info("设备 {} 从新连接 {} 上线，关闭旧连接 {}", mn, ch.remoteAddress(), old.remoteAddress());
            old.attr(MN).set(null);
            old.close();
        }
        return true;
    }

    /**
     * 连接断开时调用
     *
     * @return 这个连接绑定的 MN；没绑定或者已经被新连接顶替的返回 null
     */
    public String unbind(Channel ch) {
        String mn = ch.attr(MN).get();
        if (mn != null && channels.remove(mn, ch)) {
            return mn;
        }
        return null;
    }

    public boolean isOnline(String mn) {
        Channel ch = channels.get(mn);
        return ch != null && ch.isActive();
    }

    public boolean send(String mn, Hj212Packet packet) {
        Channel ch = channels.get(mn);
        if (ch == null || !ch.isActive()) {
            return false;
        }
        ch.writeAndFlush(packet);
        return true;
    }

    public void close(String mn) {
        Channel ch = channels.remove(mn);
        if (ch != null) {
            ch.attr(MN).set(null);
            ch.close();
        }
    }

    public int count() {
        return channels.size();
    }
}
