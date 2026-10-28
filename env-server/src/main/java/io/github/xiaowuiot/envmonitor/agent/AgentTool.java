package io.github.xiaowuiot.envmonitor.agent;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.xiaowuiot.envmonitor.user.Permission;

/**
 * 智能助手能用的一个查询工具。工具就是平台已有的查询方法，智能助手不另写一套逻辑。
 * 用户没有的权限，工具不会出现在模型的工具列表里；执行时再校验一遍。
 *
 * @param name        工具名，给模型看
 * @param description 什么时候用，给模型看
 * @param parameters  参数的 JSON Schema
 * @param permission  需要的权限，null 表示登录就能用
 * @param run         执行查询
 */
public record AgentTool(String name, String description, Map<String, Object> parameters, Permission permission,
                        Handler run) {

    @FunctionalInterface
    public interface Handler {
        ToolResult apply(JsonNode args, ToolContext ctx);
    }
}
