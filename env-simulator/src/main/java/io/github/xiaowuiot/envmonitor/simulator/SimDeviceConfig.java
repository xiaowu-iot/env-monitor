package io.github.xiaowuiot.envmonitor.simulator;

import java.util.ArrayList;
import java.util.List;

/**
 * 一台模拟数采仪的配置。
 */
public class SimDeviceConfig {

    /**
     * 一个监测因子：正常时在 base ± amplitude 之间波动
     */
    public record Factor(String code, double base, double amplitude, int decimals) {
    }

    private String host = "127.0.0.1";
    private int port = 9212;
    private String mn;
    private String password = "123456";
    /**
     * 系统编码：32 地表水体环境污染源，31 大气环境污染源，22 空气质量……
     */
    private String st = "32";
    private final List<Factor> factors = new ArrayList<>();
    private int rtdIntervalSeconds = 30;
    private int minuteIntervalSeconds = 60;
    private Scenario scenario = Scenario.NORMAL;
    /**
     * 超标场景作用在哪个因子上，为空时取第一个
     */
    private String scenarioFactor;
    /**
     * 协议版本：2017 或 2025
     */
    private String protocolVersion = "2017";
    /**
     * 2025 版的通信密钥（16 个字符）。为空时不加密
     */
    private String secretKey;
    /**
     * 2025 版心跳包（9015）间隔，0 不发
     */
    private int heartbeatSeconds = 60;

    public static SimDeviceConfig water(String mn) {
        SimDeviceConfig c = new SimDeviceConfig();
        c.mn = mn;
        c.st = "32";
        c.factors.add(new Factor("w01018", 28, 6, 1));
        c.factors.add(new Factor("w21003", 1.2, 0.4, 2));
        c.factors.add(new Factor("w01001", 7.2, 0.3, 2));
        c.factors.add(new Factor("w21011", 0.25, 0.08, 3));
        c.factors.add(new Factor("w00000", 110, 25, 1));
        return c;
    }

    public static SimDeviceConfig gas(String mn) {
        SimDeviceConfig c = new SimDeviceConfig();
        c.mn = mn;
        c.st = "31";
        c.factors.add(new Factor("a21026", 18, 5, 1));
        c.factors.add(new Factor("a21002", 45, 10, 1));
        c.factors.add(new Factor("a34013", 6, 2, 1));
        c.factors.add(new Factor("a19001", 10, 1, 1));
        return c;
    }

    public String getHost() {
        return host;
    }

    public SimDeviceConfig setHost(String host) {
        this.host = host;
        return this;
    }

    public int getPort() {
        return port;
    }

    public SimDeviceConfig setPort(int port) {
        this.port = port;
        return this;
    }

    public String getMn() {
        return mn;
    }

    public SimDeviceConfig setMn(String mn) {
        this.mn = mn;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public SimDeviceConfig setPassword(String password) {
        this.password = password;
        return this;
    }

    public String getSt() {
        return st;
    }

    public SimDeviceConfig setSt(String st) {
        this.st = st;
        return this;
    }

    public List<Factor> getFactors() {
        return factors;
    }

    public SimDeviceConfig addFactor(Factor f) {
        factors.add(f);
        return this;
    }

    public int getRtdIntervalSeconds() {
        return rtdIntervalSeconds;
    }

    public SimDeviceConfig setRtdIntervalSeconds(int rtdIntervalSeconds) {
        this.rtdIntervalSeconds = rtdIntervalSeconds;
        return this;
    }

    public int getMinuteIntervalSeconds() {
        return minuteIntervalSeconds;
    }

    public SimDeviceConfig setMinuteIntervalSeconds(int minuteIntervalSeconds) {
        this.minuteIntervalSeconds = minuteIntervalSeconds;
        return this;
    }

    public Scenario getScenario() {
        return scenario;
    }

    public SimDeviceConfig setScenario(Scenario scenario) {
        this.scenario = scenario;
        return this;
    }

    public String getScenarioFactor() {
        return scenarioFactor;
    }

    public SimDeviceConfig setScenarioFactor(String scenarioFactor) {
        this.scenarioFactor = scenarioFactor;
        return this;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public SimDeviceConfig setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion == null || protocolVersion.isBlank() ? "2017" : protocolVersion;
        return this;
    }

    public boolean isV2025() {
        return "2025".equals(protocolVersion);
    }

    public String getSecretKey() {
        return secretKey;
    }

    public SimDeviceConfig setSecretKey(String secretKey) {
        this.secretKey = secretKey;
        return this;
    }

    public int getHeartbeatSeconds() {
        return heartbeatSeconds;
    }

    public SimDeviceConfig setHeartbeatSeconds(int heartbeatSeconds) {
        this.heartbeatSeconds = heartbeatSeconds;
        return this;
    }
}
