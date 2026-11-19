package com.zifang.z.ext.spring.admin;

import com.zifang.z.ext.annotation.ExtImplType;
import com.zifang.z.ext.annotation.ExtType;
import com.zifang.z.ext.core.admin.DefaultExtAdminProvider;
import com.zifang.z.ext.core.registry.ExtImplDefinition;
import com.zifang.z.ext.core.registry.ExtPointDefinition;
import com.zifang.z.ext.core.registry.ExtRegistry;
import com.zifang.z.ext.core.store.LocalActiveImplStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 嵌入式控制面 HTTP 契约测试。
 */
class ExtAdminControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ExtRegistry.setActiveImplStore(new LocalActiveImplStore());
        ExtRegistry.clear();

        ExtPointDefinition pointDef = new ExtPointDefinition();
        pointDef.setPoint("order.service");
        pointDef.setInterfaceClass(SampleService.class);
        pointDef.setType(ExtType.SYNC);
        pointDef.setDescription("样例");
        pointDef.setVersion("1.0.0");
        ExtRegistry.registerPoint(pointDef);

        ExtImplDefinition impl = new ExtImplDefinition();
        impl.setPoint("order.service");
        impl.setName("default");
        impl.setType(ExtImplType.PLATFORM);
        impl.setImplClass(SampleServiceImpl.class);
        impl.setEnabled(true);
        impl.setOrder(1);
        ExtRegistry.registerImpl(impl);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ExtAdminController(new DefaultExtAdminProvider("test")))
                .build();
    }

    @AfterEach
    void tearDown() {
        ExtRegistry.setActiveImplStore(new LocalActiveImplStore());
        ExtRegistry.clear();
    }

    /**
     * 测试用扩展点接口。
     */
    public interface SampleService {
        String serve(String input);
    }

    /**
     * 测试用实现。
     */
    public static class SampleServiceImpl implements SampleService {
        @Override
        public String serve(String input) {
            return "served:" + input;
        }
    }

    @Test
    void listPointsReturnsWrapperWithId() throws Exception {
        mockMvc.perform(get("/api/ext/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value("order.service"))
                .andExpect(jsonPath("$.data[0].point").value("order.service"))
                .andExpect(jsonPath("$.data[0].implementations[0].id").value("order.service:default"));
    }

    @Test
    void listImplementationsReturnsFlatList() throws Exception {
        mockMvc.perform(get("/api/ext/implementations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("default"))
                .andExpect(jsonPath("$.data[0].type").value("PLATFORM"));
    }

    @Test
    void activateSwitchesActiveImplementation() throws Exception {
        ExtImplDefinition custom = new ExtImplDefinition();
        custom.setPoint("order.service");
        custom.setName("custom");
        custom.setType(ExtImplType.CUSTOM);
        custom.setImplClass(SampleServiceImpl.class);
        custom.setEnabled(true);
        ExtRegistry.registerImpl(custom);

        mockMvc.perform(post("/api/ext/points/order.service/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"implName\": \"custom\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(ExtRegistry.getActiveImpl("order.service")).isEqualTo("custom");

        mockMvc.perform(delete("/api/ext/points/order.service/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertThat(ExtRegistry.getActiveImpl("order.service")).isNull();
    }

    @Test
    void activateUnknownImplementationReturnsBusinessError() throws Exception {
        mockMvc.perform(post("/api/ext/points/order.service/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"implName\": \"nope\"}"))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void registerAndDeleteImplementation() throws Exception {
        mockMvc.perform(post("/api/ext/implementations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"point": "order.service", "name": "late", "type": "CUSTOM",
                                 "implClass": "com.zifang.z.ext.spring.admin.ExtAdminControllerTest$SampleServiceImpl",
                                 "enabled": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(ExtRegistry.getImplementations("order.service")).hasSize(2);

        mockMvc.perform(delete("/api/ext/implementations/order.service:late"))
                .andExpect(status().isOk());
        assertThat(ExtRegistry.getImplementations("order.service")).hasSize(1);
    }

    @Test
    void toggleEnabledViaPut() throws Exception {
        mockMvc.perform(put("/api/ext/implementations/order.service:default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"point\": \"order.service\", \"name\": \"default\", \"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(ExtRegistry.getImplementations("order.service").get(0).isEnabled()).isFalse();
    }

    @Test
    void deletePointRemovesImplementations() throws Exception {
        mockMvc.perform(delete("/api/ext/points/order.service"))
                .andExpect(status().isOk());

        assertThat(ExtRegistry.getPoint("order.service")).isNull();
        assertThat(ExtRegistry.getImplementations("order.service")).isEmpty();
    }
}
