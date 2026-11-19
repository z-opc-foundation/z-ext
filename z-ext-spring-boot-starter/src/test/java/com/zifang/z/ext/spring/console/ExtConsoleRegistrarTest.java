package com.zifang.z.ext.spring.console;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.WebServer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实例自登记的行为测试。
 */
class ExtConsoleRegistrarTest {

    private HttpServer server;

    private final AtomicReference<String> lastBody = new AtomicReference<>();

    private final AtomicReference<String> lastPath = new AtomicReference<>();

    private final AtomicReference<String> lastMethod = new AtomicReference<>();

    private final List<String> deletedPaths = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws java.io.IOException {
        lastMethod.set(exchange.getRequestMethod());
        lastPath.set(exchange.getRequestURI().getPath());
        if ("DELETE".equals(exchange.getRequestMethod())) {
            deletedPaths.add(exchange.getRequestURI().getPath());
        }
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int read;
            while ((read = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            lastBody.set(buffer.toString(StandardCharsets.UTF_8));
        }
        byte[] response = "{\"code\":200}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private String consoleUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void registersInstanceOnStartup() {
        ExtConsoleRegistrar registrar = new ExtConsoleRegistrar(
                consoleUrl(), "demo-svc", "http://10.0.0.9:8080", 3000);

        registrar.register(8080);

        assertThat(lastMethod.get()).isEqualTo("POST");
        assertThat(lastPath.get()).isEqualTo("/api/ext/instances");
        assertThat(lastBody.get()).contains("demo-svc").contains("http://10.0.0.9:8080");
    }

    @Test
    void unregistersOnShutdown() {
        ExtConsoleRegistrar registrar = new ExtConsoleRegistrar(
                consoleUrl(), "demo-svc", "http://10.0.0.9:8080", 3000);
        registrar.register(8080);

        registrar.unregister();

        assertThat(deletedPaths).contains("/api/ext/instances/demo-svc");
    }

    @Test
    void registrationFailureDoesNotThrow() {
        // 控制台地址不可达 ⇒ 登记失败，但不得把异常抛出去拖挂启动
        ExtConsoleRegistrar registrar = new ExtConsoleRegistrar(
                "http://127.0.0.1:1", "demo-svc", "http://10.0.0.9:8080", 500);

        registrar.register(8080);
    }

}
