package io.github.xiaowuiot.envmonitor.agent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.xiaowuiot.envmonitor.device.Device;
import io.github.xiaowuiot.envmonitor.device.DeviceRepository;
import io.github.xiaowuiot.envmonitor.device.FactorDict;
import io.github.xiaowuiot.envmonitor.device.FactorService;
import io.github.xiaowuiot.envmonitor.site.Site;
import io.github.xiaowuiot.envmonitor.site.SiteRepository;

/**
 * 把口语里的"城东污水厂""COD"解析成平台里的对象。
 */
@Component
public class AgentResolver {

    /** 常见叫法 → 因子编码 */
    static final Map<String, String> FACTOR_ALIASES = Map.ofEntries(
            Map.entry("cod", "w01018"), Map.entry("化学需氧量", "w01018"),
            Map.entry("氨氮", "w21003"), Map.entry("ph", "w01001"),
            Map.entry("总磷", "w21011"), Map.entry("总氮", "w21001"),
            Map.entry("流量", "w00000"), Map.entry("二氧化硫", "a21026"), Map.entry("so2", "a21026"),
            Map.entry("氮氧化物", "a21002"), Map.entry("nox", "a21002"),
            Map.entry("颗粒物", "a34013"), Map.entry("烟尘", "a34013"),
            Map.entry("pm2.5", "a34004"), Map.entry("pm10", "a34002"));

    private final DeviceRepository devices;
    private final SiteRepository sites;
    private final FactorService factors;

    public AgentResolver(DeviceRepository devices, SiteRepository sites, FactorService factors) {
        this.devices = devices;
        this.sites = sites;
        this.factors = factors;
    }

    /**
     * 按 MN、设备名、站点名匹配设备。text 为空或"全部"返回全部设备
     */
    public List<Device> devices(String text) {
        List<Device> all = devices.findAllByOrderByIdAsc();
        if (blank(text) || text.contains("全部") || text.contains("所有")) {
            return all;
        }
        String t = text.trim();
        Set<Long> siteIds = sitesMatching(t);
        return all.stream().filter(d -> t.contains(d.getMn()) || d.getMn().contains(t) || d.getName().contains(t)
                || t.contains(d.getName()) || (d.getSiteId() != null && siteIds.contains(d.getSiteId()))).toList();
    }

    /** 文字里提到的站点（站点名互相包含，或者用了前 4 个字的简称） */
    public Set<Long> sitesMatching(String text) {
        if (blank(text)) {
            return sites.findAll().stream().map(Site::getId).collect(Collectors.toSet());
        }
        return sites.findAll().stream()
                .filter(s -> text.contains(s.getName()) || s.getName().contains(text) || text.contains(shortName(s.getName())))
                .map(Site::getId).collect(Collectors.toSet());
    }

    static String shortName(String name) {
        return name.length() > 4 ? name.substring(0, 4) : name;
    }

    public String factor(String text) {
        if (blank(text)) {
            return null;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (factors.get(t) != null) {
            return t;
        }
        String alias = FACTOR_ALIASES.entrySet().stream().filter(e -> t.contains(e.getKey()))
                .max((a, b) -> a.getKey().length() - b.getKey().length()).map(Map.Entry::getValue).orElse(null);
        if (alias != null) {
            return alias;
        }
        return factors.all().stream().filter(f -> t.contains(f.getName().toLowerCase(Locale.ROOT)))
                .map(FactorDict::getCode).findFirst().orElse(null);
    }

    public String siteName(Long siteId) {
        return siteId == null ? "未分配站点" : sites.findById(siteId).map(Site::getName).orElse("未知站点");
    }

    static boolean blank(String s) {
        return s == null || s.isBlank() || Objects.equals(s.trim(), "null");
    }
}
