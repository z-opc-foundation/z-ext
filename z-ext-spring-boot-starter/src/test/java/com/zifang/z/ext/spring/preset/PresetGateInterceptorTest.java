package com.zifang.z.ext.spring.preset;

import com.zifang.z.ext.core.proxy.ExtProxyFactory;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.core.store.LocalActiveImplStore;
import com.zifang.z.ext.mock.MockExtImplFactory;
import com.zifang.z.ext.mock.MockSpec;
import com.zifang.z.ext.preset.PresetEventExtPoint;
import com.zifang.z.ext.preset.PresetGateExtPoint;
import com.zifang.z.ext.preset.PresetPoints;
import com.zifang.z.ext.preset.PresetRegistrar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP 闸门落点的行为测试。
 * <p>
 * 请求/响应用桩对象代替（方法名与 servlet 一致）：
 * 落点实现走反射取用这些方法，因此桩对象即可覆盖全部路径。
 */
class PresetGateInterceptorTest {

    /**
     * 请求桩。
     */
    public static class FakeRequest {

        private final Map<String, Object> attributes = new HashMap<>();

        public String getMethod() {
            return "GET";
        }

        public String getRequestURI() {
            return "/order/detail";
        }

        public String getRemoteAddr() {
            return "10.0.0.7";
        }

        public Map<String, String[]> getParameterMap() {
            return new HashMap<>();
        }

        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }
    }

    /**
     * 响应桩。
     */
    public static class FakeResponse {

        private final StringWriter body = new StringWriter();

        private int status = 200;

        private String contentType;

        public void setStatus(int status) {
            this.status = status;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public PrintWriter getWriter() {
            return new PrintWriter(body);
        }

        int status() {
            return status;
        }

        String contentType() {
            return contentType;
        }

        String body() {
            return body.toString();
        }
    }

    @BeforeEach
    void setUp() {
        ExtRegistry.setActiveImplStore(new LocalActiveImplStore());
        ExtRegistry.clear();
        PresetRegistrar.ensureRegistered();
    }

    @AfterEach
    void tearDown() {
        ExtRegistry.setActiveImplStore(new LocalActiveImplStore());
        ExtRegistry.clear();
    }

    @Test
    void allowsWhenGateAllows() {
        PresetGateInterceptor interceptor = new PresetGateInterceptor(
                new PresetGateDefaultExtPointStub(), new PresetEventExtPointStub(), "demo", 503, true);

        FakeResponse response = new FakeResponse();

        assertThat(interceptor.preHandle(new FakeRequest(), response, null)).isTrue();
        assertThat(response.status()).isEqualTo(200);
    }

    @Test
    void deniesWith503WhenGateDenies() {
        MockExtImplFactory.register(PresetPoints.GATE, PresetGateExtPoint.class,
                MockSpec.denying("mock-deny", "演练封禁"));
        ExtRegistry.setActiveImpl(PresetPoints.GATE, "mock-deny");

        // 闸门要走注册表代理 —— 控制面切激活实现才会真的改变行为
        PresetGateInterceptor interceptor = new PresetGateInterceptor(
                ExtProxyFactory.createProxy(PresetPoints.GATE), new PresetEventExtPointStub(),
                "demo", 503, true);

        FakeResponse response = new FakeResponse();

        assertThat(interceptor.preHandle(new FakeRequest(), response, null)).isFalse();
        assertThat(response.status()).isEqualTo(503);
        assertThat(response.contentType()).contains("application/json");
        assertThat(response.body()).contains("演练封禁");
    }

    @Test
    void gateFailureFallsOpenByDefault() {
        PresetGateInterceptor interceptor = new PresetGateInterceptor(
                new PresetGateExtPoint() {
                    @Override
                    public boolean allow(String event, Map<String, Object> attrs) {
                        throw new IllegalStateException("控制面实现炸了");
                    }

                    @Override
                    public String denyMessage(String event, Map<String, Object> attrs) {
                        return null;
                    }
                },
                new PresetEventExtPointStub(), "demo", 503, true);

        FakeResponse response = new FakeResponse();

        assertThat(interceptor.preHandle(new FakeRequest(), response, null)).isTrue();
        assertThat(response.status()).isEqualTo(200);
    }

    /**
     * 闸门默认实现（放行）。
     */
    static class PresetGateDefaultExtPointStub implements PresetGateExtPoint {

        @Override
        public boolean allow(String event, Map<String, Object> attrs) {
            return true;
        }

        @Override
        public String denyMessage(String event, Map<String, Object> attrs) {
            return null;
        }
    }

    /**
     * 通知点空实现。
     */
    static class PresetEventExtPointStub implements PresetEventExtPoint {

        @Override
        public void publish(String event, Map<String, Object> attrs) {
            // 测试里不关心通知
        }
    }
}
