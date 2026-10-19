package io.github.xiaowuiot.envmonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 平台配置，对应 application.yml 里的 env.*
 */
@ConfigurationProperties(prefix = "env")
public record EnvProperties(
        @DefaultValue Hj212 hj212,
        @DefaultValue Device device,
        @DefaultValue Ai ai,
        @DefaultValue Dingtalk dingtalk,
        @DefaultValue Init init,
        @DefaultValue Simulator simulator,
        @DefaultValue Demo demo,
        @DefaultValue("./data") String dataDir) {

    /**
     * @param port             TCP 监听端口
     * @param ackData          收到要求应答的数据时回 9014
     * @param rawRetentionDays 原始报文保留天数
     */
    public record Hj212(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("9212") int port,
            @DefaultValue("true") boolean ackData,
            @DefaultValue("7") int rawRetentionDays) {
    }

    /**
     * @param offlineTimeoutSeconds 超过这么久没收到任何报文，判为离线
     */
    public record Device(@DefaultValue("300") int offlineTimeoutSeconds) {
    }

    /**
     * 智能助手。provider=mock 时不调用大模型，用内置规则理解常用问法；provider=openai 时调用 OpenAI 兼容接口
     */
    public record Ai(@DefaultValue("mock") String provider,
                     String baseUrl,
                     String apiKey,
                     String model,
                     @DefaultValue("60") int timeoutSeconds) {
    }

    /**
     * 钉钉群机器人。webhook 为空时不推送钉钉，只发站内消息
     *
     * @param webhook     群机器人的 Webhook 地址
     * @param secret      加签密钥，SEC 开头
     * @param mockEnabled 打开内置的钉钉机器人模拟接口 /mock/dingtalk，没有真实群机器人时用它演示
     * @param mockSecret  模拟接口校验加签用的密钥
     */
    public record Dingtalk(String webhook,
                           String secret,
                           @DefaultValue("true") boolean mockEnabled,
                           @DefaultValue("SECdemo0000000000000000000000000000000000000000000000000000000000") String mockSecret) {
    }

    /**
     * @param adminPassword 首次启动时管理员的密码，不填就随机生成并写到 data/initial-admin.txt
     * @param demoData      首次启动时生成演示数据（站点、设备、规则、演示账号）
     * @param demoPassword  演示账号的密码
     */
    public record Init(String adminPassword,
                       @DefaultValue("true") boolean demoData,
                       @DefaultValue("demo123") String demoPassword) {
    }

    /**
     * 公开演示站的数据保护。打开后，除 admins 以外的账号：
     * 不能修改、删除开启保护时已有的站点、设备、预警规则、账号（自己新建的可以随便改删）；
     * 不能改密码，不能管账号，不能清空钉钉模拟消息，不能操作内置模拟器。
     * 第一次开启时和之后每天凌晨把数据库备份到 data/backup（只支持 H2 文件库）。
     *
     * @param protect    打开保护，公开演示站用
     * @param admins     不受限制的账号
     * @param backupKeep 保留最近几份备份，0 表示不备份
     */
    public record Demo(@DefaultValue("false") boolean protect,
                       @DefaultValue("admin") java.util.List<String> admins,
                       @DefaultValue("7") int backupKeep) {
    }

    /**
     * @param autoStart 启动时自动把所有设备都跑起来
     */
    public record Simulator(@DefaultValue("false") boolean autoStart,
                            @DefaultValue("10") int rtdIntervalSeconds,
                            @DefaultValue("60") int minuteIntervalSeconds) {
    }
}
