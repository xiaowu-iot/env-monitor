package io.github.xiaowuiot.envmonitor.agent;

/**
 * 查询类工具的结果。text 是写好的中文摘要（规则模式直接展示），data 是结构化数据（交给大模型自己组织语言）。
 */
public record ToolResult(String text, Object data) {

    public static ToolResult of(String text) {
        return new ToolResult(text, null);
    }
}
