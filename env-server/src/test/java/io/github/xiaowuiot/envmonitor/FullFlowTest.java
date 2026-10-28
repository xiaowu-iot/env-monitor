package io.github.xiaowuiot.envmonitor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.xiaowuiot.envmonitor.alarm.Alarm;
import io.github.xiaowuiot.envmonitor.alarm.AlarmRepository;
import io.github.xiaowuiot.envmonitor.alarm.AlarmStatus;
import io.github.xiaowuiot.envmonitor.data.DataType;
import io.github.xiaowuiot.envmonitor.data.LatestValueRepository;
import io.github.xiaowuiot.envmonitor.data.MonitorDataRepository;
import io.github.xiaowuiot.envmonitor.device.Device;
import io.github.xiaowuiot.envmonitor.device.DeviceRepository;
import io.github.xiaowuiot.envmonitor.notify.MockDingTalkController;
import io.github.xiaowuiot.envmonitor.order.WorkOrder;
import io.github.xiaowuiot.envmonitor.order.WorkOrderLog;
import io.github.xiaowuiot.envmonitor.order.WorkOrderLogRepository;
import io.github.xiaowuiot.envmonitor.order.WorkOrderRepository;
import io.github.xiaowuiot.envmonitor.sim.SimulatorService;
import io.github.xiaowuiot.envmonitor.simulator.Scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 端到端：模拟器上报 → 入库 → 超标预警 → 派给站点负责人 → 钉钉（模拟群，校验加签）→ 处理 → 驳回 → 再处理 → 审核通过。
 * 全部走真实的 TCP 连接和 HTTP 接口。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18170",
        "env.hj212.port=19312",
        "env.data-dir=target/test-data",
        "spring.datasource.url=jdbc:h2:mem:flow;DB_CLOSE_DELAY=-1",
        "env.init.admin-password=admin123",
        "env.dingtalk.webhook=http://127.0.0.1:18170/mock/dingtalk/robot/send?access_token=demo",
        "env.simulator.rtd-interval-seconds=1",
        "env.simulator.minute-interval-seconds=5"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullFlowTest {

    static final int PORT = 18170;
    static final String EAST_MN = "88888880000001";
    static final String POWER_MN = "88888880000003";

    @Autowired
    SimulatorService sim;
    @Autowired
    DeviceRepository devices;
    @Autowired
    AlarmRepository alarms;
    @Autowired
    WorkOrderRepository orders;
    @Autowired
    WorkOrderLogRepository logs;
    @Autowired
    LatestValueRepository latest;
    @Autowired
    MonitorDataRepository data;
    @Autowired
    MockDingTalkController mockDingTalk;

    Device device(String mn) {
        return devices.findByMn(mn).orElseThrow();
    }

    Alarm openAlarm(Long deviceId, String factor) {
        return alarms.findAll().stream()
                .filter(a -> a.getDeviceId().equals(deviceId) && factor.equals(a.getFactorCode()) && a.getStatus() != AlarmStatus.CLOSED)
                .findFirst().orElse(null);
    }

    @Test
    @Order(1)
    void reportAndOnline() {
        sim.start(device(EAST_MN), Scenario.NORMAL);
        await().atMost(Duration.ofSeconds(15)).until(() -> device(EAST_MN).isOnline());
        await().atMost(Duration.ofSeconds(15)).until(() -> latest.findByDeviceId(device(EAST_MN).getId()).size() >= 5);
        await().atMost(Duration.ofSeconds(20)).until(() -> data.findAll().stream()
                .anyMatch(d -> d.getDataType() == DataType.MINUTE && d.getDeviceId().equals(device(EAST_MN).getId())));
        Api admin = Api.login(PORT, "admin", "admin123");
        assertThat(admin.get("/api/data/latest").body().size()).isEqualTo(3);
        // 9014 应答模拟器收到了
        await().atMost(Duration.ofSeconds(5)).until(() -> sim.get(EAST_MN).status().acked() > 0);
    }

    @Test
    @Order(2)
    void overLimitDispatchNotifyHandleReview() {
        mockDingTalk.clear();
        Long id = device(EAST_MN).getId();
        sim.get(EAST_MN).setScenario(Scenario.OVER_LIMIT, "w01018");
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            Alarm a = openAlarm(id, "w01018");
            return a != null && a.getOrderId() != null;
        });
        Alarm a = openAlarm(id, "w01018");
        WorkOrder o = orders.findById(a.getOrderId()).orElseThrow();
        assertThat(o.getHandlerName()).isEqualTo("张工");
        assertThat(o.getReviewerName()).isEqualTo("陈主管");

        await().atMost(Duration.ofSeconds(10)).until(() -> mockDingTalk.messages().stream()
                .anyMatch(m -> m.title().startsWith("预警派单")));
        MockDingTalkController.Received msg = mockDingTalk.messages().stream()
                .filter(m -> m.title().startsWith("预警派单")).findFirst().orElseThrow();
        assertThat(msg.signOk()).isTrue();
        assertThat(msg.atMobiles()).contains("13800000002");

        sim.get(EAST_MN).setScenario(Scenario.NORMAL, null);
        await().atMost(Duration.ofSeconds(15)).until(() -> alarms.findById(a.getId()).orElseThrow().isRecovered());

        Api liu = Api.login(PORT, "liu", "demo123");
        assertThat(liu.post("/api/orders/" + o.getId() + "/submit", Map.of("result", "我来")).status()).isEqualTo(403);
        Api zhang = Api.login(PORT, "zhang", "demo123");
        assertThat(zhang.get("/api/orders?box=todo").body().path("total").asLong()).isEqualTo(1);
        assertThat(zhang.post("/api/orders/" + o.getId() + "/submit", Map.of("result", "进水负荷突增，已调整曝气")).status()).isEqualTo(200);
        Api chen = Api.login(PORT, "chen", "demo123");
        assertThat(chen.post("/api/orders/" + o.getId() + "/review", Map.of("approve", false)).status()).isEqualTo(400);
        assertThat(chen.post("/api/orders/" + o.getId() + "/review", Map.of("approve", false, "comment", "补充数据")).status()).isEqualTo(200);
        assertThat(zhang.post("/api/orders/" + o.getId() + "/submit", Map.of("result", "COD 已降到 28")).status()).isEqualTo(200);
        assertThat(chen.post("/api/orders/" + o.getId() + "/review", Map.of("approve", true)).status()).isEqualTo(200);

        assertThat(orders.findById(o.getId()).orElseThrow().getStatus()).isEqualTo(WorkOrder.Status.APPROVED);
        assertThat(alarms.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(AlarmStatus.CLOSED);
        List<WorkOrderLog.Action> actions = logs.findByOrderIdOrderByIdAsc(o.getId()).stream().map(WorkOrderLog::getAction).toList();
        assertThat(actions).containsSubsequence(WorkOrderLog.Action.CREATE, WorkOrderLog.Action.NOTIFY,
                WorkOrderLog.Action.RECOVER, WorkOrderLog.Action.SUBMIT, WorkOrderLog.Action.REJECT,
                WorkOrderLog.Action.SUBMIT, WorkOrderLog.Action.APPROVE);
        assertThat(mockDingTalk.messages()).allMatch(MockDingTalkController.Received::signOk);
    }

    @Test
    @Order(3)
    void siteWithoutHandlerNeedsManualDispatch() {
        Long id = device(POWER_MN).getId();
        sim.start(device(POWER_MN), Scenario.OVER_LIMIT);
        await().atMost(Duration.ofSeconds(20)).until(() -> openAlarm(id, "a21026") != null);
        Alarm a = openAlarm(id, "a21026");
        assertThat(a.getStatus()).isEqualTo(AlarmStatus.PENDING);
        Api chen = Api.login(PORT, "chen", "demo123");
        assertThat(chen.get("/api/inbox/unread").body().path("count").asLong()).isGreaterThan(0);
        JsonNode users = chen.get("/api/users/options").body();
        long liuId = 0;
        for (JsonNode u : users) {
            if ("刘工".equals(u.path("name").asText())) {
                liuId = u.path("id").asLong();
            }
        }
        // 处理人没有派单权限
        Api zhang = Api.login(PORT, "zhang", "demo123");
        assertThat(zhang.post("/api/alarms/" + a.getId() + "/dispatch", Map.of("handlerId", liuId)).status()).isEqualTo(403);
        assertThat(chen.post("/api/alarms/" + a.getId() + "/dispatch", Map.of("handlerId", liuId)).status()).isEqualTo(200);
        assertThat(orders.findById(alarms.findById(a.getId()).orElseThrow().getOrderId()).orElseThrow().getHandlerName())
                .isEqualTo("刘工");
        sim.stopAll();
        await().atMost(Duration.ofSeconds(10)).until(() -> !device(EAST_MN).isOnline());
    }
}
