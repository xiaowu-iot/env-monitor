package io.github.xiaowuiot.envmonitor;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class EnvMonitorApplication {

    public static void main(String[] args) {
        // 212 报文里的时间都是现场当地时间，统一按北京时间处理
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(EnvMonitorApplication.class, args);
    }
}
