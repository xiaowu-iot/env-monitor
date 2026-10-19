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
 * 编码、中文名称和缺省计量单位取自 HJ212-2017 附录 B：表 B.1 水监测因子编码表（引用 HJ 525-2009）、
 * 表 B.2 气监测因子编码表（引用 HJ 524-2009），单位写成符号（如"毫克/升"写作 mg/L，"无量纲"留空）。
 * 这里只列了常用的二十几个，作为平台因子字典的初始数据，平台上可以修改和补充。
 * <p>
 * 编码首字母：w 水、a 气（附录 B 表 B.1、B.2），e 污水工况、g 烟气工况（6.4.1），i 现场端信息（6.4.2）。
 * 注意 a34002、a34004 在表 B.2 里的缺省单位是"纳克/立方米"，环境空气颗粒物常用 μg/m³ 报送，接入时按设备实际单位改。
 */
public final class Hj212Factors {

    public static final Pattern CODE_2017 = Pattern.compile("[a-z]\\d{5}");

    public record Factor(String code, String name, String unit, String category) {
    }

    private static final Map<String, Factor> FACTORS = new LinkedHashMap<>();

    static {
        // 水
        add("w00000", "污水", "L/s", "水");
        add("w01001", "pH 值", "", "水");
        add("w01009", "溶解氧", "mg/L", "水");
        add("w01010", "水温", "℃", "水");
        add("w01014", "电导率", "μS/cm", "水");
        add("w01018", "化学需氧量", "mg/L", "水");
        add("w01019", "高锰酸盐指数", "mg/L", "水");
        add("w21001", "总氮", "mg/L", "水");
        add("w21003", "氨氮", "mg/L", "水");
        add("w21011", "总磷", "mg/L", "水");
        // 废气（污染源）
        add("a00000", "废气", "m³/s", "气");
        add("a01011", "烟气流速", "m/s", "气");
        add("a01012", "烟气温度", "℃", "气");
        add("a01013", "烟气压力", "kPa", "气");
        add("a01014", "烟气湿度", "%", "气");
        add("a19001", "氧气含量", "%", "气");
        add("a21002", "氮氧化物", "mg/m³", "气");
        add("a21026", "二氧化硫", "mg/m³", "气");
        add("a34013", "烟尘", "mg/m³", "气");
        // 环境空气
        add("a21004", "二氧化氮", "mg/m³", "气");
        add("a21005", "一氧化碳", "mg/m³", "气");
        add("a34002", "可吸入颗粒物 PM10", "ng/m³", "气");
        add("a34004", "细微颗粒物 PM2.5", "ng/m³", "气");
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
     * 是不是监测因子编码（i 开头的是现场端信息编码，见标准 6.4.2，不算因子）
     */
    public static boolean isFactorCode(String code) {
        return code != null && CODE_2017.matcher(code).matches() && code.charAt(0) != 'i';
    }
}
