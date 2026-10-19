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
 */
public final class Hj212Commands {

    public static final String SET_TIMEOUT = "1000";
    public static final String GET_TIME = "1011";
    public static final String SET_TIME = "1012";
    public static final String TIME_CALIBRATION_REQ = "1013";
    public static final String GET_RTD_INTERVAL = "1061";
    public static final String SET_RTD_INTERVAL = "1062";
    public static final String GET_MIN_INTERVAL = "1063";
    public static final String SET_MIN_INTERVAL = "1064";
    public static final String SET_PASSWORD = "1072";

    public static final String REALTIME = "2011";
    public static final String STOP_REALTIME = "2012";
    public static final String RUN_STATUS = "2021";
    public static final String STOP_RUN_STATUS = "2022";
    public static final String DAY = "2031";
    public static final String RUN_TIME_DAY = "2041";
    public static final String MINUTE = "2051";
    public static final String HOUR = "2061";
    public static final String RESTART_TIME = "2081";

    public static final String CALIBRATE = "3011";
    public static final String SAMPLE_NOW = "3012";
    public static final String CLEAN = "3013";
    public static final String COMPARE_SAMPLE = "3014";
    public static final String OVER_LIMIT_SAMPLE = "3015";
    public static final String SET_SAMPLE_PERIOD = "3016";
    public static final String GET_SAMPLE_PERIOD = "3017";
    public static final String GET_SAMPLE_OUT_TIME = "3018";
    public static final String GET_SN = "3019";
    public static final String GET_INFO = "3020";
    public static final String SET_INFO = "3021";

    public static final String REQUEST_ACK = "9011";
    public static final String EXEC_RESULT = "9012";
    public static final String NOTIFY_ACK = "9013";
    public static final String DATA_ACK = "9014";


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

    // ===== 数据标记 xxx-Flag，标准 6.6.4 表 8《数据标记表》 =====

    /**
     * 数据标记（表 8）：N 仪器工作正常、F 仪器停运、M 仪器处于维护期间产生的数据、S 手工输入的设定值、
     * D 仪器故障、C 仪器处于校准状态、T 仪器采样数值超过测量上限、B 仪器与数采仪通讯异常。
     * <p>
     * 哪些标记的数据算有效，标准里没有规定，下面是本平台的处理规则：停运、维护、故障、校准、通讯异常时的数据
     * 不是真实排放，<b>不参与超标判断</b>；超测量上限（T）说明实际值比读数还高，照常参与判断。
     */
    public static boolean isValidFlag(String flag) {
        return flag == null || "N".equals(flag) || "T".equals(flag) || "S".equals(flag);
    }

    public static String flagName(String flag) {
        if (flag == null) {
            return "未标记";
        }
        switch (flag) {
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
            case OVER_LIMIT_SAMPLE: return "超标留样";
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
