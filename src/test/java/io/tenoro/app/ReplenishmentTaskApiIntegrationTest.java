package io.tenoro.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReplenishmentTaskApiIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
        location("PICK-01", "PICKING");
        location("RSV-01", "RESERVE");
        location("RSV-02", "RESERVE");
        mockMvc.perform(post("/replenishment-rules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\",\"min\":20,\"max\":100}"))
                .andExpect(status().isCreated());
        stock("SKU-100", "PICK-01", 5);
        stock("SKU-100", "RSV-01", 60);
        stock("SKU-100", "RSV-02", 50);
    }

    @Test
    void evaluatesAndPlansTasks() throws Exception {
        mockMvc.perform(post("/replenishment/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PLANNED"))
                .andExpect(jsonPath("$.targetQuantity").value(100))
                .andExpect(jsonPath("$.requiredQuantity").value(95))
                .andExpect(jsonPath("$.allocatedQuantity").value(95))
                .andExpect(jsonPath("$.shortfall").value(0))
                .andExpect(jsonPath("$.tasks", hasSize(2)))
                .andExpect(jsonPath("$.tasks[0].fromLocation").value("RSV-01"))
                .andExpect(jsonPath("$.tasks[0].quantity").value(60))
                .andExpect(jsonPath("$.tasks[0].status").value("OPEN"))
                .andExpect(jsonPath("$.tasks[0].id").isNotEmpty())
                .andExpect(jsonPath("$.tasks[0].createdAt").isNotEmpty());
    }

    @Test
    void notRequiredWhenAtOrAboveMin() throws Exception {
        stock("SKU-100", "PICK-01", 20);
        mockMvc.perform(post("/replenishment/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.requiredQuantity").value(0))
                .andExpect(jsonPath("$.tasks", hasSize(0)));
    }

    @Test
    void unavailableWhenNoAssignableReserveStock() throws Exception {
        stock("SKU-100", "RSV-01", 0);
        stock("SKU-100", "RSV-02", 0);
        mockMvc.perform(post("/replenishment/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.requiredQuantity").value(95))
                .andExpect(jsonPath("$.allocatedQuantity").value(0))
                .andExpect(jsonPath("$.shortfall").value(95))
                .andExpect(jsonPath("$.tasks", hasSize(0)));
    }

    @Test
    void partiallyPlannedWhenReserveCannotCoverRequired() throws Exception {
        location("PICK-02", "PICKING");
        location("RSV-03", "RESERVE");
        mockMvc.perform(post("/replenishment-rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-300\",\"locationCode\":\"PICK-02\",\"min\":30,\"max\":120}"))
                .andExpect(status().isCreated());
        stock("SKU-300", "PICK-02", 10);
        stock("SKU-300", "RSV-03", 70);
        mockMvc.perform(post("/replenishment/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-300\",\"locationCode\":\"PICK-02\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PARTIALLY_PLANNED"))
                .andExpect(jsonPath("$.requiredQuantity").value(110))
                .andExpect(jsonPath("$.allocatedQuantity").value(70))
                .andExpect(jsonPath("$.shortfall").value(40))
                .andExpect(jsonPath("$.tasks", hasSize(1)));
    }

    @Test
    void unknownLocation404() throws Exception {
        mockMvc.perform(post("/replenishment/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-99\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOCATION_NOT_FOUND"));
    }

    @Test
    void reserveLocation400() throws Exception {
        mockMvc.perform(post("/replenishment/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"RSV-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOCATION_NOT_PICKING"));
    }

    @Test
    void missingRule404() throws Exception {
        location("PICK-02", "PICKING");
        mockMvc.perform(post("/replenishment/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-02\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RULE_NOT_FOUND"));
    }

    @Test
    void duplicateEvaluation409() throws Exception {
        mockMvc.perform(post("/replenishment/tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\"}"));
        mockMvc.perform(post("/replenishment/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPLENISHMENT_IN_PROGRESS"));
    }

    @Test
    void listsTasksAndConfirmsAndCancels() throws Exception {
        mockMvc.perform(get("/replenishment/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        MvcResult created = mockMvc.perform(post("/replenishment/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        String firstId = body.path("tasks").get(0).path("id").asText();
        String secondId = body.path("tasks").get(1).path("id").asText();

        mockMvc.perform(get("/replenishment/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].status").value("OPEN"));

        mockMvc.perform(post("/replenishment/tasks/" + firstId + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.id").value(firstId));

        mockMvc.perform(post("/replenishment/tasks/" + secondId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/replenishment/tasks/" + firstId + "/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_NOT_OPEN"));
        mockMvc.perform(post("/replenishment/tasks/" + secondId + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_NOT_OPEN"));
    }

    @Test
    void malformedAndUnknownIds() throws Exception {
        mockMvc.perform(post("/replenishment/tasks/not-a-uuid/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/replenishment/tasks/not-a-uuid/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/replenishment/tasks/1-1-1-1-1/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/replenishment/tasks/1-1-1-1-1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String unknown = UUID.randomUUID().toString();
        mockMvc.perform(post("/replenishment/tasks/" + unknown + "/confirm"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
        mockMvc.perform(post("/replenishment/tasks/" + unknown + "/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    @Test
    void confirmDestinationTargetExceeded409() throws Exception {
        MvcResult created = mockMvc.perform(post("/replenishment/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("tasks").get(0).path("id").asText();
        stock("SKU-100", "PICK-01", 50);
        mockMvc.perform(post("/replenishment/tasks/" + id + "/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DESTINATION_TARGET_EXCEEDED"));
        mockMvc.perform(get("/replenishment/tasks"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    private void location(String code, String type) throws Exception {
        mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"type\":\"" + type + "\"}"));
    }

    private void stock(String sku, String location, long qty) throws Exception {
        mockMvc.perform(post("/stock").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"locationCode\":\"" + location + "\",\"quantity\":" + qty + "}"))
                .andExpect(status().isOk());
    }
}
