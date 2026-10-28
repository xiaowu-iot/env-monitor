package io.github.xiaowuiot.envmonitor.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 监测因子字典。初始数据来自 HJ212-2017 附录里的常用编码，可以在后台补充（比如地方平台约定的编码）。
 */
@Entity
@Table(name = "env_factor")
public class FactorDict {

    @Id
    @Column(length = 16)
    private String code;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(length = 16)
    private String unit;

    /** 水 / 气 / 其他 */
    @Column(length = 8)
    private String category;

    /** 页面显示保留几位小数 */
    private int decimals = 2;

    protected FactorDict() {
    }

    public FactorDict(String code, String name, String unit, String category, int decimals) {
        this.code = code;
        this.name = name;
        this.unit = unit;
        this.category = category;
        this.decimals = decimals;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getDecimals() {
        return decimals;
    }

    public void setDecimals(int decimals) {
        this.decimals = decimals;
    }
}
