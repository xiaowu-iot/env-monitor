package io.github.xiaowuiot.envmonitor.hj212;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 常用监测因子编码。
 * <p>
 * 2017 版的因子编码是"1 个小写字母 + 5 位数字"：w 开头是水，a 开头是气。完整编码表在标准附录里，
 * 这里只列了常见的几十个，作为平台因子字典的初始数据；名称和单位以标准原文为准，平台上可以修改和补充。
 */
public final class Hj212Factors {

    public static final Pattern CODE_2017 = Pattern.compile("[a-z]\\d{5}");

    public record Factor(String code, String name, String unit, String category) {
    }

    private static final Map<String, Factor> FACTORS = new LinkedHashMap<>();

    static {
        // 水
        add("w00000", "污水排放量", "L/s", "水");
        add("w01001", "pH 值", "", "水");
        add("w01003", "浊度", "NTU", "水");
        add("w01009", "溶解氧", "mg/L", "水");
        add("w01010", "水温", "℃", "水");
        add("w01014", "电导率", "μS/cm", "水");
        add("w01018", "化学需氧量", "mg/L", "水");
        add("w01019", "高锰酸盐指数", "mg/L", "水");
        add("w21001", "总氮", "mg/L", "水");
        add("w21003", "氨氮", "mg/L", "水");
        add("w21011", "总磷", "mg/L", "水");
        // 废气（污染源）
        add("a00000", "废气排放量", "m³/s", "气");
        add("a01011", "烟气流速", "m/s", "气");
        add("a01012", "烟气温度", "℃", "气");
        add("a01013", "烟气压力", "kPa", "气");
        add("a01014", "烟气湿度", "%", "气");
        add("a19001", "氧气含量", "%", "气");
        add("a21002", "氮氧化物", "mg/m³", "气");
        add("a21026", "二氧化硫", "mg/m³", "气");
        add("a34013", "颗粒物", "mg/m³", "气");
        // 环境空气
        add("a21004", "二氧化氮", "μg/m³", "气");
        add("a21005", "一氧化碳", "mg/m³", "气");
        add("a05024", "臭氧", "μg/m³", "气");
        add("a34002", "PM10", "μg/m³", "气");
        add("a34004", "PM2.5", "μg/m³", "气");
    }

    private Hj212Factors() {
    }

    private static void add(String code, String name, String unit, String category) {
        FACTORS.put(code, new Factor(code, name, unit, category));
    }

    /**
     * @return 没登记的返回 null
     */
    public static Factor get(String code) {
        return FACTORS.get(code);
    }

    public static List<Factor> all() {
        return Collections.unmodifiableList(new ArrayList<>(FACTORS.values()));
    }

    /**
     * 是不是监测因子编码（i1xxxx 是现场机信息编码，不算因子）
     */
    public static boolean isFactorCode(String code) {
        return code != null && CODE_2017.matcher(code).matches() && code.charAt(0) != 'i';
    }
}
