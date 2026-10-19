package io.github.xiaowuiot.envmonitor.data;

import io.github.xiaowuiot.envmonitor.hj212.Hj212Commands;

/**
 * 数据类型，和 212 的数据命令一一对应。
 */
public enum DataType {

    REALTIME(Hj212Commands.REALTIME, "实时数据"),
    MINUTE(Hj212Commands.MINUTE, "分钟数据"),
    HOUR(Hj212Commands.HOUR, "小时数据"),
    DAY(Hj212Commands.DAY, "日数据");

    private final String cn;
    private final String label;

    DataType(String cn, String label) {
        this.cn = cn;
        this.label = label;
    }

    public String cn() {
        return cn;
    }

    public String label() {
        return label;
    }

    public static DataType ofCn(String cn) {
        for (DataType t : values()) {
            if (t.cn.equals(cn)) {
                return t;
            }
        }
        return null;
    }
}
