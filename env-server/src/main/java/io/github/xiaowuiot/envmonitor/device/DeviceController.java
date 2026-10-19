package io.github.xiaowuiot.envmonitor.device;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.xiaowuiot.envmonitor.common.ApiException;
import io.github.xiaowuiot.envmonitor.common.PageResult;
import io.github.xiaowuiot.envmonitor.ingest.RawMessage;
import io.github.xiaowuiot.envmonitor.ingest.RawMessageRepository;
import io.github.xiaowuiot.envmonitor.ingest.SessionRegistry;
import io.github.xiaowuiot.envmonitor.security.CurrentUser;
import io.github.xiaowuiot.envmonitor.site.Site;
import io.github.xiaowuiot.envmonitor.site.SiteRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 站点、设备、因子字典、未登记设备、原始报文。
 */
@RestController
@RequestMapping("/api")
public class DeviceController {

    private final DeviceRepository devices;
    private final SiteRepository sites;
    private final FactorService factors;
    private final RawMessageRepository raws;
    private final SessionRegistry sessions;
    private final DeviceStateService state;
    private final CurrentUser current;

    public DeviceController(DeviceRepository devices, SiteRepository sites, FactorService factors,
                            RawMessageRepository raws, SessionRegistry sessions, DeviceStateService state,
                            CurrentUser current) {
        this.devices = devices;
        this.sites = sites;
        this.factors = factors;
        this.raws = raws;
        this.sessions = sessions;
        this.state = state;
        this.current = current;
    }

    public record DeviceView(Long id, String mn, String name, Long siteId, String siteName, String st, boolean enabled,
                             boolean online, LocalDateTime lastSeenAt, String lastIp, String protocolVersion,
                             List<String> factorCodes, List<String> factorNames, boolean hasPassword, String remark) {
    }

    public record DeviceRequest(
            @NotBlank(message = "请输入 MN 号") @Pattern(regexp = "[0-9A-Za-z]{8,32}", message = "MN 号为 8–32 位字母或数字") String mn,
            @NotBlank(message = "请输入设备名称") String name,
            Long siteId, String password, String st, Boolean enabled, List<String> factorCodes, String remark) {
    }

    @GetMapping("/devices")
    @PreAuthorize("hasAuthority('PERM_DEVICE_VIEW')")
    public List<DeviceView> list(@RequestParam(name = "siteId", required = false) Long siteId,
                                 @RequestParam(name = "keyword", required = false) String keyword,
                                 @RequestParam(name = "online", required = false) Boolean online) {
        return devices.findAllByOrderByIdAsc().stream()
                .filter(d -> siteId == null || siteId.equals(d.getSiteId()))
                .filter(d -> online == null || d.isOnline() == online)
                .filter(d -> keyword == null || keyword.isBlank() || d.getMn().contains(keyword) || d.getName().contains(keyword))
                .map(this::view).toList();
    }

    @GetMapping("/devices/{id}")
    @PreAuthorize("hasAuthority('PERM_DEVICE_VIEW')")
    public DeviceView get(@PathVariable Long id) {
        return view(check(id));
    }

    @PostMapping("/devices")
    @PreAuthorize("hasAuthority('PERM_DEVICE_MANAGE')")
    @Transactional
    public DeviceView create(@Valid @RequestBody DeviceRequest body) {
        if (devices.existsByMn(body.mn())) {
            throw ApiException.badRequest("MN 号 " + body.mn() + " 已经登记过了");
        }
        Device d = new Device(body.mn(), body.name().strip(), body.siteId());
        apply(d, body);
        devices.save(d);
        // 登记之前收到的报文归到这台设备上
        raws.bindDevice(d.getMn(), d.getId());
        return view(d);
    }

    @PutMapping("/devices/{id}")
    @PreAuthorize("hasAuthority('PERM_DEVICE_MANAGE')")
    @Transactional
    public DeviceView update(@PathVariable Long id, @Valid @RequestBody DeviceRequest body) {
        Device d = check(id);
        if (!d.getMn().equals(body.mn())) {
            if (devices.existsByMn(body.mn())) {
                throw ApiException.badRequest("MN 号 " + body.mn() + " 已经登记过了");
            }
            sessions.close(d.getMn());
            d.setMn(body.mn());
        }
        d.setName(body.name().strip());
        d.setSiteId(body.siteId());
        boolean wasEnabled = d.isEnabled();
        apply(d, body);
        devices.save(d);
        if (wasEnabled && !d.isEnabled()) {
            // 停用：断开连接，不再接收数据
            sessions.close(d.getMn());
            state.offline(d.getMn(), "设备已停用");
        }
        return view(d);
    }

    private void apply(Device d, DeviceRequest body) {
        if (body.password() != null) {
            d.setPassword(body.password().isBlank() ? null : body.password().trim());
        }
        d.setSt(body.st() == null || body.st().isBlank() ? "32" : body.st());
        if (body.enabled() != null) {
            d.setEnabled(body.enabled());
        }
        if (body.factorCodes() != null) {
            d.setFactorCodes(body.factorCodes());
        }
        d.setRemark(body.remark());
    }

    @DeleteMapping("/devices/{id}")
    @PreAuthorize("hasAuthority('PERM_DEVICE_MANAGE')")
    public Map<String, Boolean> delete(@PathVariable Long id) {
        Device d = check(id);
        sessions.close(d.getMn());
        devices.delete(d);
        return Map.of("ok", true);
    }

    @GetMapping("/devices/{id}/raw")
    @PreAuthorize("hasAuthority('PERM_DEVICE_VIEW')")
    public PageResult<RawMessage> raw(@PathVariable Long id, @RequestParam(name = "cn", required = false) String cn,
                                      @RequestParam(name = "page", defaultValue = "0") int page,
                                      @RequestParam(name = "size", defaultValue = "50") int size) {
        Device d = check(id);
        PageRequest p = PageRequest.of(page, Math.min(size, 200));
        return PageResult.of(cn == null || cn.isBlank() ? raws.findByMnOrderByIdDesc(d.getMn(), p)
                : raws.findByMnAndCnOrderByIdDesc(d.getMn(), cn, p), r -> r);
    }

    public record UnknownDevice(String mn, long count, LocalDateTime lastAt, String remote) {
    }

    /** 发过报文但还没登记的 MN */
    @GetMapping("/devices/unknown")
    @PreAuthorize("hasAuthority('PERM_DEVICE_MANAGE')")
    public List<UnknownDevice> unknown() {
        return raws.unknownDevices().stream().filter(r -> !devices.existsByMn((String) r[0]))
                .map(r -> new UnknownDevice((String) r[0], (Long) r[1], (LocalDateTime) r[2], (String) r[3])).toList();
    }

    public Device check(Long id) {
        current.get();
        return devices.findById(id).orElseThrow(() -> ApiException.notFound("设备不存在"));
    }

    public DeviceView view(Device d) {
        Site s = d.getSiteId() == null ? null : sites.findById(d.getSiteId()).orElse(null);
        return new DeviceView(d.getId(), d.getMn(), d.getName(), d.getSiteId(), s == null ? null : s.getName(),
                d.getSt(), d.isEnabled(), d.isOnline(), d.getLastSeenAt(), d.getLastIp(), d.getProtocolVersion(),
                d.getFactorCodes(), d.getFactorCodes().stream().map(factors::name).toList(),
                d.getPassword() != null && !d.getPassword().isEmpty(), d.getRemark());
    }

    // ===================== 站点 =====================

    public record SiteView(Long id, String name, String address, Long handlerId, Long reviewerId, String remark,
                           long deviceCount, long onlineCount) {
    }

    public record SiteRequest(@NotBlank(message = "请输入站点名称") String name, String address, Long handlerId,
                              Long reviewerId, String remark) {
    }

    @GetMapping("/sites")
    public List<SiteView> sites() {
        current.get();
        Map<Long, List<Device>> bySite = devices.findAll().stream().filter(d -> d.getSiteId() != null)
                .collect(Collectors.groupingBy(Device::getSiteId));
        return sites.findAllByOrderByIdAsc().stream().map(s -> {
            List<Device> ds = bySite.getOrDefault(s.getId(), List.of());
            return new SiteView(s.getId(), s.getName(), s.getAddress(), s.getHandlerId(), s.getReviewerId(),
                    s.getRemark(), ds.size(), ds.stream().filter(Device::isOnline).count());
        }).toList();
    }

    @PostMapping("/sites")
    @PreAuthorize("hasAuthority('PERM_DEVICE_MANAGE')")
    public Site createSite(@Valid @RequestBody SiteRequest body) {
        Site s = new Site(body.name().strip());
        applySite(s, body);
        return sites.save(s);
    }

    @PutMapping("/sites/{id}")
    @PreAuthorize("hasAuthority('PERM_DEVICE_MANAGE')")
    public Site updateSite(@PathVariable Long id, @Valid @RequestBody SiteRequest body) {
        Site s = sites.findById(id).orElseThrow(() -> ApiException.notFound("站点不存在"));
        s.setName(body.name().strip());
        applySite(s, body);
        return sites.save(s);
    }

    private static void applySite(Site s, SiteRequest b) {
        s.setAddress(b.address());
        s.setHandlerId(b.handlerId());
        s.setReviewerId(b.reviewerId());
        s.setRemark(b.remark());
    }

    @DeleteMapping("/sites/{id}")
    @PreAuthorize("hasAuthority('PERM_DEVICE_MANAGE')")
    public Map<String, Boolean> deleteSite(@PathVariable Long id) {
        if (!devices.findBySiteId(id).isEmpty()) {
            throw ApiException.badRequest("站点下还有设备，请先移走或删除设备");
        }
        sites.deleteById(id);
        return Map.of("ok", true);
    }

    // ===================== 因子字典 =====================

    public record FactorRequest(@NotBlank(message = "请输入因子编码") String code, @NotBlank(message = "请输入因子名称") String name,
                                String unit, String category, Integer decimals) {
    }

    @GetMapping("/factors")
    public List<FactorDict> factors() {
        current.get();
        return factors.all();
    }

    @PostMapping("/factors")
    @PreAuthorize("hasAuthority('PERM_DEVICE_MANAGE')")
    public FactorDict saveFactor(@Valid @RequestBody FactorRequest body) {
        FactorDict f = factors.get(body.code().trim());
        if (f == null) {
            f = new FactorDict(body.code().trim(), body.name().trim(), body.unit(), body.category(),
                    body.decimals() == null ? 2 : body.decimals());
        } else {
            f.setName(body.name().trim());
            f.setUnit(body.unit());
            f.setCategory(body.category());
            f.setDecimals(body.decimals() == null ? 2 : body.decimals());
        }
        return factors.save(f);
    }
}
