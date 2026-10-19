package io.github.xiaowuiot.envmonitor;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 测试用的小 HTTP 客户端：带 Cookie，像浏览器一样登录后调接口。
 */
class Api {

    private static final ObjectMapper JSON = new ObjectMapper();

    record Resp(int status, JsonNode body) {
    }

    private final String base;
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager())
            .connectTimeout(Duration.ofSeconds(5)).build();

    Api(int port) {
        this.base = "http://127.0.0.1:" + port;
    }

    static Api login(int port, String username, String password) {
        Api api = new Api(port);
        Resp r = api.post("/api/auth/login", "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        if (r.status() != 200) {
            throw new IllegalStateException("登录失败 " + username + "：" + r.body());
        }
        return api;
    }

    Resp get(String path) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET());
    }

    Resp post(String path, String json) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    Resp post(String path, Object body) {
        try {
            return post(path, JSON.writeValueAsString(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Resp send(HttpRequest.Builder b) {
        try {
            HttpResponse<String> r = http.send(b.timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = r.body() == null || r.body().isBlank() ? null : JSON.readTree(r.body());
            return new Resp(r.statusCode(), body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
