package io.github.xiaowuiot.envmonitor.device;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * 因子字典的内存缓存。预警、通知、智能助手都要频繁把编码翻译成名称。
 */
@Service
public class FactorService {

    private final FactorDictRepository repo;
    private final Map<String, FactorDict> cache = new ConcurrentHashMap<>();

    public FactorService(FactorDictRepository repo) {
        this.repo = repo;
    }

    public void reload() {
        cache.clear();
        repo.findAll().forEach(f -> cache.put(f.getCode(), f));
    }

    public FactorDict get(String code) {
        if (cache.isEmpty()) {
            reload();
        }
        return code == null ? null : cache.get(code);
    }

    public String name(String code) {
        FactorDict f = get(code);
        return f == null ? code : f.getName();
    }

    public String unit(String code) {
        FactorDict f = get(code);
        return f == null || f.getUnit() == null ? "" : f.getUnit();
    }

    /** 名称带单位，比如"化学需氧量(mg/L)" */
    public String label(String code) {
        String unit = unit(code);
        return name(code) + (unit.isEmpty() ? "" : "(" + unit + ")");
    }

    public List<FactorDict> all() {
        if (cache.isEmpty()) {
            reload();
        }
        return cache.values().stream().sorted(Comparator.comparing(FactorDict::getCode)).toList();
    }

    public FactorDict save(FactorDict f) {
        FactorDict saved = repo.save(f);
        cache.put(saved.getCode(), saved);
        return saved;
    }
}
