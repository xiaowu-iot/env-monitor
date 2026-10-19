package io.github.xiaowuiot.envmonitor.simulator;

import java.util.Locale;

/**
 * 命令行启动模拟器。
 * <pre>
 * java -jar env-simulator-exec.jar --host 127.0.0.1 --port 9212 --count 5 --type water --interval 10 \
 *      --mn-prefix 8888888000 --password 123456 --scenario NORMAL
 * </pre>
 * MN 由前缀加序号组成，一共 14 位（也可以用 24 位，前缀给长一些）。启动后每 30 秒打印一次各设备状态，Ctrl+C 退出。
 */
public final class SimulatorMain {

    private SimulatorMain() {
    }

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 9212;
        int count = 1;
        String type = "water";
        int interval = 30;
        int minuteInterval = 60;
        String prefix = "8888888000";
        String password = "123456";
        Scenario scenario = Scenario.NORMAL;
        for (int i = 0; i < args.length - 1; i += 2) {
            String v = args[i + 1];
            switch (args[i]) {
                case "--host" -> host = v;
                case "--port" -> port = Integer.parseInt(v);
                case "--count" -> count = Integer.parseInt(v);
                case "--type" -> type = v;
                case "--interval" -> interval = Integer.parseInt(v);
                case "--minute-interval" -> minuteInterval = Integer.parseInt(v);
                case "--mn-prefix" -> prefix = v;
                case "--password" -> password = v;
                case "--scenario" -> scenario = Scenario.valueOf(v.toUpperCase(Locale.ROOT));
                default -> throw new IllegalArgumentException("不认识的参数：" + args[i]);
            }
        }
        Simulator simulator = new Simulator();
        Runtime.getRuntime().addShutdownHook(new Thread(simulator::close));
        int width = Math.max(14 - prefix.length(), 4);
        for (int i = 1; i <= count; i++) {
            String mn = prefix + String.format("%0" + width + "d", i);
            SimDeviceConfig c = "gas".equals(type) ? SimDeviceConfig.gas(mn) : SimDeviceConfig.water(mn);
            c.setHost(host).setPort(port).setPassword(password).setRtdIntervalSeconds(interval)
                    .setMinuteIntervalSeconds(minuteInterval).setScenario(scenario);
            simulator.start(c);
        }
        System.out.printf("已启动 %d 台模拟设备，连接 %s:%d，场景：%s%n", count, host, port, scenario.label());
        while (true) {
            Thread.sleep(30_000);
            for (SimStatus s : simulator.statuses()) {
                System.out.printf("%s %s 已发 %d 条，已应答 %d 条，最新值 %s%n", s.mn(),
                        s.connected() ? "在线" : "离线", s.sent(), s.acked(), s.lastValues());
            }
        }
    }
}
