package io.github.xiaowuiot.envmonitor.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.xiaowuiot.envmonitor.alarm.AlarmRule;
import io.github.xiaowuiot.envmonitor.alarm.AlarmRuleRepository;
import io.github.xiaowuiot.envmonitor.device.Device;
import io.github.xiaowuiot.envmonitor.device.DeviceRepository;
import io.github.xiaowuiot.envmonitor.device.FactorDict;
import io.github.xiaowuiot.envmonitor.device.FactorDictRepository;
import io.github.xiaowuiot.envmonitor.device.FactorService;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Factors;
import io.github.xiaowuiot.envmonitor.site.Site;
import io.github.xiaowuiot.envmonitor.site.SiteRepository;
import io.github.xiaowuiot.envmonitor.user.Permission;
import io.github.xiaowuiot.envmonitor.user.Role;
import io.github.xiaowuiot.envmonitor.user.RoleRepository;
import io.github.xiaowuiot.envmonitor.user.User;
import io.github.xiaowuiot.envmonitor.user.UserRepository;

import static io.github.xiaowuiot.envmonitor.user.Permission.*;

/**
 * 首次启动初始化：三个内置角色、管理员、因子字典；打开 env.init.demo-data 时再生成一套演示数据，
 * 启动后接上模拟器就能看到完整流程。
 */
@Component
@Order(1)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final SiteRepository sites;
    private final DeviceRepository devices;
    private final FactorDictRepository factorRepo;
    private final FactorService factors;
    private final AlarmRuleRepository alarmRules;
    private final PasswordEncoder encoder;
    private final EnvProperties props;

    public DataInitializer(UserRepository users, RoleRepository roles, SiteRepository sites, DeviceRepository devices,
                           FactorDictRepository factorRepo, FactorService factors, AlarmRuleRepository alarmRules,
                           PasswordEncoder encoder, EnvProperties props) {
        this.users = users;
        this.roles = roles;
        this.sites = sites;
        this.devices = devices;
        this.factorRepo = factorRepo;
        this.factors = factors;
        this.alarmRules = alarmRules;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (factorRepo.count() == 0) {
            for (Hj212Factors.Factor f : Hj212Factors.all()) {
                factorRepo.save(new FactorDict(f.code(), f.name(), f.unit(), f.category(), "w21011".equals(f.code()) ? 3 : 2));
            }
        }
        factors.reload();
        if (users.count() > 0) {
            return;
        }
        Role admin = roles.save(new Role("系统管理员", "全部权限", true, EnumSet.allOf(Permission.class)));
        Role reviewer = roles.save(new Role("审核员", "审核处理结果，也可以手动派单", true,
                EnumSet.of(DEVICE_VIEW, ALARM_VIEW, ORDER_DISPATCH, ORDER_REVIEW, AGENT_USE)));
        Role handler = roles.save(new Role("处理人", "现场处理预警", true,
                EnumSet.of(DEVICE_VIEW, ALARM_VIEW, ORDER_HANDLE, AGENT_USE)));

        String adminPassword = props.init().adminPassword();
        if (adminPassword == null || adminPassword.isBlank()) {
            adminPassword = randomPassword();
            Path file = Path.of(props.dataDir(), "initial-admin.txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "管理员账号：admin\n初始密码：" + adminPassword + "\n登录后请尽快修改密码，然后删除本文件。\n",
                    StandardCharsets.UTF_8);
            log.info("已创建管理员 admin，初始密码见 {}", file.toAbsolutePath());
        }
        User a = new User("admin", encoder.encode(adminPassword), "管理员");
        a.setRoles(Set.of(admin));
        users.save(a);

        if (props.init().demoData()) {
            String pw = encoder.encode(props.init().demoPassword());
            User chen = user("chen", "陈主管", "13800000001", pw, reviewer);
            User zhang = user("zhang", "张工", "13800000002", pw, handler);
            User liu = user("liu", "刘工", "13800000003", pw, handler);
            Site s1 = site("城东污水处理厂总排口", "东环路 18 号", zhang, chen);
            Site s2 = site("城西工业园污水排口", "西园大道 6 号", liu, chen);
            Site s3 = site("城西热电厂 1 号烟囱", "热电路 1 号", null, null);
            device("88888880000001", "城东污水厂在线监测", s1, "32", List.of("w01018", "w21003", "w01001", "w21011", "w00000"));
            device("88888880000002", "工业园排口在线监测", s2, "32", List.of("w01018", "w21003", "w01001", "w21011", "w00000"));
            device("88888880000003", "热电厂烟气在线监测", s3, "31", List.of("a21026", "a21002", "a34013", "a19001"));
            alarmRules.save(new AlarmRule("COD 超标", "w01018", 50.0, null, 2));
            alarmRules.save(new AlarmRule("氨氮超标", "w21003", 5.0, null, 2));
            alarmRules.save(new AlarmRule("pH 超出范围", "w01001", 9.0, 6.0, 2));
            alarmRules.save(new AlarmRule("总磷超标", "w21011", 0.5, null, 2));
            alarmRules.save(new AlarmRule("二氧化硫超标", "a21026", 35.0, null, 2));
            alarmRules.save(new AlarmRule("氮氧化物超标", "a21002", 50.0, null, 2));
            alarmRules.save(new AlarmRule("颗粒物超标", "a34013", 10.0, null, 2));
            log.info("已生成演示数据：3 个站点、3 台设备、7 条预警规则，演示账号密码 {}", props.init().demoPassword());
        }
    }

    private User user(String username, String name, String phone, String pwHash, Role role) {
        User u = new User(username, pwHash, name);
        u.setPhone(phone);
        u.setRoles(Set.of(role));
        return users.save(u);
    }

    /** 热电厂故意不配负责人，演示"待派单 → 手动派单" */
    private Site site(String name, String address, User handler, User reviewer) {
        Site s = new Site(name);
        s.setAddress(address);
        s.setHandlerId(handler == null ? null : handler.getId());
        s.setReviewerId(reviewer == null ? null : reviewer.getId());
        return sites.save(s);
    }

    private void device(String mn, String name, Site site, String st, List<String> factorCodes) {
        Device d = new Device(mn, name, site.getId());
        d.setPassword("123456");
        d.setSt(st);
        d.setFactorCodes(factorCodes);
        devices.save(d);
    }

    private static String randomPassword() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
