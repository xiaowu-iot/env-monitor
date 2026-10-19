package io.github.xiaowuiot.envmonitor.hj212;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 212 里的时间格式：QN 是 yyyyMMddHHmmssSSS，DataTime、SystemTime 这些是 yyyyMMddHHmmss，都是现场当地时间。
 */
public final class Hj212Time {

    public static final DateTimeFormatter QN = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    public static final DateTimeFormatter DATA_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final AtomicLong LAST_QN = new AtomicLong();

    private Hj212Time() {
    }

    /**
     * 生成一个不重复的 QN。QN 用来匹配请求和应答，同一毫秒发两条命令时往后顺延 1 毫秒
     */
    public static String nextQn() {
        long now = Long.parseLong(LocalDateTime.now().format(QN));
        long qn = LAST_QN.updateAndGet(last -> Math.max(last + 1, now));
        return Long.toString(qn);
    }

    public static String format(LocalDateTime time) {
        return time.format(DATA_TIME);
    }

    /**
     * @return 格式不对返回 null
     */
    public static LocalDateTime parse(String value) {
        if (value == null || value.length() < 14) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.substring(0, 14), DATA_TIME);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
