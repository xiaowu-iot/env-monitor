package io.github.xiaowuiot.envmonitor.agent;

import java.util.ArrayList;
import java.util.List;

import io.github.xiaowuiot.envmonitor.user.User;

/**
 * 一次对话里工具执行的上下文：谁在用，以及工具顺带产出的图表。
 */
public class ToolContext {

    /**
     * 曲线图：工具查了历史数据，页面上直接画出来
     */
    public record Chart(String title, String unit, Double upper, Double lower, List<Object[]> points) {
    }

    private final User user;
    private final List<Chart> charts = new ArrayList<>();

    public ToolContext(User user) {
        this.user = user;
    }

    public User user() {
        return user;
    }

    public void addChart(Chart c) {
        charts.add(c);
    }

    public List<Chart> charts() {
        return charts;
    }
}
