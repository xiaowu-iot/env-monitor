package io.github.xiaowuiot.envmonitor.hj212;

/**
 * HJ212-2017 的命令编码（CN）和交互结果，出自标准 6.6.5 表 9《命令编码表》。
 * <pre>
 * 类别       CN            含义
 * 初始化     1000          设置超时时间及重发次数
 * 参数       1011 / 1012   提取 / 设置现场机时间
 *           1013          现场机时间校准请求（通知命令，现场机 → 上位机，上位机回 9013 后下发 1012，见表 C.4）
 *           1061 / 1062   提取 / 设置实时数据间隔
 *           1063 / 1064   提取 / 设置分钟数据间隔
 *           1072          设置现场机访问密码（表 9 写作"设置现场机密码"，表 C.9 写作"设置现场机访问密码"）
 * 数据       2011 / 2012   取实时数据 / 停止察看实时数据（2012 是通知命令，现场机回 9013）
 *           2021 / 2022   取设备运行状态 / 停止察看（2022 是通知命令，现场机回 9013）
 *           2031          日历史数据
 *           2041          设备运行时间日历史数据
 *           2051 / 2061   分钟数据 / 小时数据
 *           2081          上传数采仪开机时间（只有上传命令，现场机 → 上位机）
 * 控制       3011          零点校准量程校准
 *           3012          即时采样
 *           3013          启动清洗 / 反吹
 *           3014          比对采样
 *           3015          超标留样
 *           3016 / 3017   设置 / 提取采样时间周期
 *           3018          提取出样时间
 *           3019          提取设备唯一标识
 *           3020          提取现场机信息
 *           3021          设置现场机参数
 * 交互       9011          请求应答
 *           9012          执行结果
 *           9013          通知应答
 *           9014          数据应答
 * </pre>
 * 每条命令 CP 里带哪些字段见标准附录 C 的命令示例。
 * <p>
 * <b>HJ212-2025</b>（2026-01-01 实施）在 6.7.5 表 12 里新增了这些命令：
 * <pre>
 *           1001          上传数采仪硬件序号（通知，现场机 → 上位机，没激活时 MN 为空，上位机回 9013 后下发 1002）
 *           1002          设置数采仪 MN 编码（上位机按硬件指纹给数采仪赋 MN）
 *           1014          现场机获取新密钥（现场机 → 上位机，上位机回 9013）/ 上位机下发新密钥（请求命令），都要加密
 *           2013          原始监测数据（周期性监测，比如废水混合样、VOCs）
 *           2052          噪声单次测量数据（20 分钟一个时段，见 HJ 1402）
 *           2062          自动标样核查（校准）数据
 *           2111          上传炉膛温度 5 分钟均值（垃圾焚烧）
 *           3015          改成"上传留样信息"（现场机 → 上位机），2017 版是上位机下发"超标留样"
 *           3022          下发即时留样任务
 *           3024          启动标样核查
 *           9015          心跳包（现场机发，上位机回 9013）
 * </pre>
 * 2025 版表 12 里不再有 3014 比对采样、3016 / 3017 采样时间周期，接 2017 版设备时照常可用。
 */
public final class Hj212Commands {

    public static final String SET_TIMEOUT = "1000";
    public static final String HARDWARE_SN = "1001";
    public static final String SET_MN = "1002";
    public static final String GET_TIME = "1011";
    public static final String SET_TIME = "1012";
    public static final String TIME_CALIBRATION_REQ = "1013";
    public static final String SECRET_KEY = "1014";
    public static final String GET_RTD_INTERVAL = "1061";
    public static final String SET_RTD_INTERVAL = "1062";
    public static final String GET_MIN_INTERVAL = "1063";
    public static final String SET_MIN_INTERVAL = "1064";
    public static final String SET_PASSWORD = "1072";

    public static final String REALTIME = "2011";
    public static final String STOP_REALTIME = "2012";
    public static final String RAW_DATA = "2013";
    public static final String RUN_STATUS = "2021";
    public static final String STOP_RUN_STATUS = "2022";
    public static final String DAY = "2031";
    public static final String RUN_TIME_DAY = "2041";
    public static final String MINUTE = "2051";
    public static final String NOISE_ONCE = "2052";
    public static final String HOUR = "2061";
    public static final String STANDARD_CHECK_DATA = "2062";
    public static final String RESTART_TIME = "2081";
    public static final String FURNACE_TEMP = "2111";

    public static final String CALIBRATE = "3011";
    public static final String SAMPLE_NOW = "3012";
    public static final String CLEAN = "3013";
    public static final String COMPARE_SAMPLE = "3014";
    /**
     * 2017 版：上位机下发"超标留样"；2025 版：现场机上传留样信息（VaseNo、i43001~i43007）
     */
    public static final String OVER_LIMIT_SAMPLE = "3015";
    public static final String SET_SAMPLE_PERIOD = "3016";
    public static final String GET_SAMPLE_PERIOD = "3017";
    public static final String GET_SAMPLE_OUT_TIME = "3018";
    public static final String GET_SN = "3019";
    public static final String GET_INFO = "3020";
    public static final String SET_INFO = "3021";
    public static final String SAMPLE_TASK = "3022";
    public static final String START_STANDARD_CHECK = "3024";

    public static final String REQUEST_ACK = "9011";
    public static final String EXEC_RESULT = "9012";
    public static final String NOTIFY_ACK = "9013";
    public static final String DATA_ACK = "9014";
    public static final String HEARTBEAT = "9015";

    // ===== 请求应答 QnRtn，标准 6.6.3 表 7《请求命令返回表》 =====
    public static final int QN_READY = 1;
    public static final int QN_REJECTED = 2;
    public static final int QN_BAD_PW = 3;
    public static final int QN_BAD_MN = 4;
    public static final int QN_BAD_ST = 5;
    public static final int QN_BAD_FLAG = 6;
    public static final int QN_BAD_QN = 7;
    public static final int QN_BAD_CN = 8;
    public static final int QN_BAD_CRC = 9;
    public static final int QN_UNKNOWN = 100;

    // ===== 执行结果 ExeRtn，标准 6.6.2 表 6《执行结果定义表》 =====
    public static final int EXE_SUCCESS = 1;
    public static final int EXE_FAILED = 2;
    public static final int EXE_BAD_CONDITION = 3;
    public static final int EXE_TIMEOUT = 4;
    public static final int EXE_BUSY = 5;
    public static final int EXE_FAULT = 6;
    public static final int EXE_NO_DATA = 100;

    private Hj212Commands() {
    }

    public static String qnRtnName(int v) {
        switch (v) {
            case QN_READY:
                return "准备执行请求";
            case QN_REJECTED:
                return "请求被拒绝";
            case QN_BAD_PW:
                return "PW 错误";
            case QN_BAD_MN:
                return "MN 错误";
            case QN_BAD_ST:
                return "ST 错误";
            case QN_BAD_FLAG:
                return "Flag 错误";
            case QN_BAD_QN:
                return "QN 错误";
            case QN_BAD_CN:
                return "CN 错误";
            case QN_BAD_CRC:
                return "CRC 校验错误";
            default:
                return "未知错误";
        }
    }

    public static String exeRtnName(int v) {
        switch (v) {
            case EXE_SUCCESS:
                return "执行成功";
            case EXE_FAILED:
                return "执行失败，但不知道原因";
            case EXE_BAD_CONDITION:
                return "命令请求条件错误";
            case EXE_TIMEOUT:
                return "通讯超时";
            case EXE_BUSY:
                return "系统繁忙不能执行";
            case EXE_FAULT:
                return "系统故障";
            case EXE_NO_DATA:
                return "没有数据";
            default:
                return "未知结果 " + v;
        }
    }

    // ===== 数据标记 xxx-Flag =====

    /**
     * 2017 版（6.6.4 表 8）：N 仪器工作正常、F 仪器停运、M 仪器处于维护期间产生的数据、S 手工输入的设定值、
     * D 仪器故障、C 仪器处于校准状态、T 仪器采样数值超过测量上限、B 仪器与数采仪通讯异常。
     * <p>
     * 2025 版（6.7.4）把标记改成最长 3 个字符、区分大小写，分两张表：
     * <ul>
     *     <li>表 10 自动监测设备标记：N 数据有效、D 故障（含断电、现场端内部通信故障）、C 校准、M 日常维护、
     *     T 超量程、Td 关键工况参数故障。同时有多种时按 D、C、M、T、Td 的顺序认定</li>
     *     <li>表 11 生产工况标记：N 正常运行、St 启动（Stc 冷启动、Sth 热启动）、Sd 停炉（机）、B 闷炉、
     *     F 停运（废气）/ 停排（废水）、Sr 故障 / 事故</li>
     * </ul>
     * 注意 B 在两版里含义不同：2017 版是"通讯异常"，2025 版是"闷炉"；S 在 2025 版不再使用。
     * <p>
     * 哪些标记的数据参与超标判断，标准里没有规定，下面是本平台的处理规则：
     * 停运、维护、故障、校准、通讯异常、启停炉时的数据不是正常工况下的排放，<b>不参与超标判断</b>；
     * 超量程（T）说明实际值比读数还高（2025 版 8.1.1 也规定按量程上限作为有效数据），照常判断；
     * 故障 / 事故（Sr）是"无法正常运行且持续排放污染物"，更要判断。
     */
    public static boolean isValidFlag(String flag) {
        return flag == null || "N".equals(flag) || "T".equals(flag) || "S".equals(flag) || "Sr".equals(flag);
    }

    /**
     * 按协议版本取标记的中文名：B 在 2025 版是闷炉
     */
    public static String flagName(String flag, int version) {
        if (version >= Hj212Packet.VERSION_2025 && "B".equals(flag)) {
            return "闷炉";
        }
        return flagName(flag);
    }

    public static String flagName(String flag) {
        if (flag == null) {
            return "未标记";
        }
        switch (flag) {
            case "Td":
                return "关键工况参数故障";
            case "St":
                return "启动";
            case "Stc":
                return "冷启动";
            case "Sth":
                return "热启动";
            case "Sd":
                return "停炉（机）";
            case "Sr":
                return "故障 / 事故";
            case "N":
                return "正常";
            case "F":
                return "停运";
            case "M":
                return "维护";
            case "S":
                return "手工输入";
            case "D":
                return "故障";
            case "C":
                return "校准";
            case "T":
                return "超测量上限";
            case "B":
                return "通讯异常";
            default:
                return "未知标记 " + flag;
        }
    }

    /**
     * 是不是数据上传类命令（2xxx）
     */
    public static boolean isData(String cn) {
        return cn != null && cn.startsWith("2");
    }

    /**
     * 命令的中文名，日志和页面上用
     */
    public static String name(String cn) {
        if (cn == null) {
            return "未知命令";
        }
        switch (cn) {
            case SET_TIMEOUT: return "设置超时时间及重发次数";
            case HARDWARE_SN: return "上传数采仪硬件序号";
            case SET_MN: return "设置数采仪 MN 编码";
            case SECRET_KEY: return "密钥更新";
            case RAW_DATA: return "原始监测数据";
            case NOISE_ONCE: return "噪声单次测量数据";
            case STANDARD_CHECK_DATA: return "自动标样核查（校准）数据";
            case FURNACE_TEMP: return "炉膛温度 5 分钟均值";
            case SAMPLE_TASK: return "下发即时留样任务";
            case START_STANDARD_CHECK: return "启动标样核查";
            case HEARTBEAT: return "心跳包";
            case GET_TIME: return "提取现场机时间";
            case SET_TIME: return "设置现场机时间";
            case TIME_CALIBRATION_REQ: return "现场机时间校准请求";
            case GET_RTD_INTERVAL: return "提取实时数据间隔";
            case SET_RTD_INTERVAL: return "设置实时数据间隔";
            case GET_MIN_INTERVAL: return "提取分钟数据间隔";
            case SET_MIN_INTERVAL: return "设置分钟数据间隔";
            case SET_PASSWORD: return "设置现场机访问密码";
            case REALTIME: return "实时数据";
            case STOP_REALTIME: return "停止察看实时数据";
            case RUN_STATUS: return "设备运行状态";
            case STOP_RUN_STATUS: return "停止察看设备运行状态";
            case DAY: return "日历史数据";
            case RUN_TIME_DAY: return "设备运行时间日历史数据";
            case MINUTE: return "分钟数据";
            case HOUR: return "小时数据";
            case RESTART_TIME: return "数采仪开机时间";
            case CALIBRATE: return "零点 / 量程校准";
            case SAMPLE_NOW: return "即时采样";
            case CLEAN: return "启动清洗 / 反吹";
            case COMPARE_SAMPLE: return "比对采样";
            case OVER_LIMIT_SAMPLE: return "超标留样 / 留样信息";
            case SET_SAMPLE_PERIOD: return "设置采样时间周期";
            case GET_SAMPLE_PERIOD: return "提取采样时间周期";
            case GET_SAMPLE_OUT_TIME: return "提取出样时间";
            case GET_SN: return "提取设备唯一标识";
            case GET_INFO: return "提取现场机信息";
            case SET_INFO: return "设置现场机参数";
            case REQUEST_ACK: return "请求应答";
            case EXEC_RESULT: return "执行结果";
            case NOTIFY_ACK: return "通知应答";
            case DATA_ACK: return "数据应答";
            default: return "命令 " + cn;
        }
    }
}
