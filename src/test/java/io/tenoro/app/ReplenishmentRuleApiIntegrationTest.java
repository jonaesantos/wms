package io.tenoro.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReplenishmentRuleApiIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
        mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"PICK-01\",\"type\":\"PICKING\"}"));
        mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"RSV-01\",\"type\":\"RESERVE\"}"));
    }

    @Test
    void createsRuleWith201() throws Exception {
        mockMvc.perform(post("/replenishment-rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\",\"min\":20,\"max\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-100"))
                .andExpect(jsonPath("$.locationCode").value("PICK-01"))
                .andExpect(jsonPath("$.min").value(20))
                .andExpect(jsonPath("$.max").value(100));
    }

    @Test
    void rejectsDuplicateWith409() throws Exception {
        mockMvc.perform(post("/replenishment-rules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\",\"min\":20,\"max\":100}"));
        mockMvc.perform(post("/replenishment-rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\",\"min\":10,\"max\":50}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RULE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void rejectsReserveLocationWith400() throws Exception {
        mockMvc.perform(post("/replenishment-rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"RSV-01\",\"min\":20,\"max\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOCATION_NOT_PICKING"))
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void rejectsUnknownLocationWith404() throws Exception {
        mockMvc.perform(post("/replenishment-rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-99\",\"min\":20,\"max\":100}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOCATION_NOT_FOUND"));
    }

    @Test
    void rejectsMinGreaterThanMaxWith400() throws Exception {
        mockMvc.perform(post("/replenishment-rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\",\"min\":50,\"max\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void rejectsMissingMinWith400() throws Exception {
        mockMvc.perform(post("/replenishment-rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\",\"max\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
