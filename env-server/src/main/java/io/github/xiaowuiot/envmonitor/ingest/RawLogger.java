package io.github.xiaowuiot.envmonitor.ingest;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.xiaowuiot.envmonitor.config.EnvProperties;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Format;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Packet;

/**
 * 记录收发的原始报文，并按保留天数清理。
 */
@Component
public class RawLogger {

    private final RawMessageRepository repo;
    private final EnvProperties props;

    public RawLogger(RawMessageRepository repo, EnvProperties props) {
        this.repo = repo;
        this.props = props;
    }

    public RawMessage in(String raw, Hj212Packet p, Long deviceId, String remote, String error) {
        RawMessage m = new RawMessage(RawMessage.IN, p == null ? null : p.getMn(), deviceId,
                p == null ? null : p.getCn(), p == null ? null : p.getQn(), raw, remote);
        if (error != null) {
            m.fail(error);
        }
        return repo.save(m);
    }

    public void out(Hj212Packet p, Long deviceId) {
        String frame = Hj212Format.frame(Hj212Format.format(p)).trim();
        repo.save(new RawMessage(RawMessage.OUT, p.getMn(), deviceId, p.getCn(), p.getQn(), frame, null));
    }

    @Scheduled(cron = "0 17 3 * * *")
    public void cleanup() {
        repo.deleteBefore(LocalDateTime.now().minusDays(props.hj212().rawRetentionDays()));
    }
}
