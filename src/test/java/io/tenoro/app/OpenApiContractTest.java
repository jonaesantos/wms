package io.tenoro.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI contract assertions (task 5.2): the documented paths, required fields,
 * integer formats/bounds, response schemas, and statuses for the five inventory
 * paths must match runtime behavior.
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
class OpenApiContractTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode openApi;

    @BeforeEach
    void setUp() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
        String body = mockMvc.perform(get("/openapi"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        openApi = objectMapper.readTree(body);
    }

    private JsonNode responses(String path, String method) {
        JsonNode op = openApi.path("paths").path(path).path(method);
        assertFalse(op.isMissingNode(), method + " " + path + " must be documented");
        return op.path("responses");
    }

    private void assertStatuses(String path, String method, String... statuses) {
        JsonNode responses = responses(path, method);
        for (String status : statuses) {
            assertTrue(responses.has(status),
                    method + " " + path + " must document status " + status);
        }
    }

    @Test
    void documentsAllFiveInventoryPaths() {
        JsonNode paths = openApi.path("paths");
        assertTrue(paths.path("/locations").has("post"));
        assertTrue(paths.path("/locations").has("get"));
        assertTrue(paths.path("/stock").has("post"));
        assertTrue(paths.path("/stock").has("get"));
        assertTrue(paths.path("/stock/move").has("post"));
        assertTrue(paths.path("/replenishment-rules").has("post"));
        assertTrue(paths.path("/replenishment/tasks").has("post"));
        assertTrue(paths.path("/replenishment/tasks").has("get"));
        assertTrue(paths.path("/replenishment/tasks/{id}/confirm").has("post"));
        assertTrue(paths.path("/replenishment/tasks/{id}/cancel").has("post"));
    }

    @Test
    void documentsResponseStatusesMatchingRuntime() {
        assertStatuses("/locations", "post", "201", "400", "409");
        assertStatuses("/locations", "get", "200");
        assertStatuses("/stock", "post", "200", "400", "404");
        assertStatuses("/stock", "get", "200", "400");
        assertStatuses("/stock/move", "post", "200", "400", "404", "409");
        assertStatuses("/replenishment-rules", "post", "201", "400", "404", "409");
        assertStatuses("/replenishment/tasks", "post", "200", "400", "404", "409");
        assertStatuses("/replenishment/tasks", "get", "200");
        JsonNode listSchema = openApi.path("paths").path("/replenishment/tasks").path("get")
                .path("responses").path("200").path("content").path("application/json").path("schema");
        assertEquals("array", listSchema.path("type").asText());
        assertTrue(listSchema.path("items").path("$ref").asText().endsWith("/ReplenishmentTaskResponse"));
        assertStatuses("/replenishment/tasks/{id}/confirm", "post", "200", "400", "404", "409");
        assertStatuses("/replenishment/tasks/{id}/cancel", "post", "200", "400", "404", "409");
    }

    @Test
    void documentsRequestSchemasWithRequiredFieldsAndBounds() {
        JsonNode schemas = openApi.path("components").path("schemas");

        for (String schema : new String[]{
                "CreateLocationRequest", "LocationResponse", "EstablishStockRequest",
                "StockResponse", "MoveStockRequest", "MoveStockResponse", "ApiErrorResponse",
                "CreateReplenishmentRuleRequest", "ReplenishmentRuleResponse",
                "EvaluateReplenishmentRequest", "ReplenishmentEvaluationResponse",
                "ReplenishmentTaskResponse"}) {
            assertFalse(schemas.path(schema).isMissingNode(), "schema " + schema + " must be documented");
        }

        JsonNode establishQuantity = schemas.path("EstablishStockRequest").path("properties").path("quantity");
        assertEquals("integer", establishQuantity.path("type").asText());
        assertEquals("int64", establishQuantity.path("format").asText());
        assertEquals(0, establishQuantity.path("minimum").asInt());

        JsonNode moveQuantity = schemas.path("MoveStockRequest").path("properties").path("quantity");
        assertEquals("int64", moveQuantity.path("format").asText());
        assertEquals(1, moveQuantity.path("minimum").asInt());

        JsonNode ruleMin = schemas.path("CreateReplenishmentRuleRequest").path("properties").path("min");
        assertEquals("integer", ruleMin.path("type").asText());
        assertEquals("int64", ruleMin.path("format").asText());
        assertEquals(0, ruleMin.path("minimum").asInt());
        JsonNode ruleRequired = schemas.path("CreateReplenishmentRuleRequest").path("required");
        assertTrue(required(ruleRequired, "sku"));
        assertTrue(required(ruleRequired, "locationCode"));
        assertTrue(required(ruleRequired, "min"));
        assertTrue(required(ruleRequired, "max"));

        JsonNode establishRequired = schemas.path("EstablishStockRequest").path("required");
        assertTrue(required(establishRequired, "sku"));
        assertTrue(required(establishRequired, "locationCode"));
        assertTrue(required(establishRequired, "quantity"));

        JsonNode typeProp = schemas.path("CreateLocationRequest").path("properties").path("type");
        JsonNode typeEnum = typeProp.path("enum");
        assertTrue(required(typeEnum, "PICKING"));
        assertTrue(required(typeEnum, "RESERVE"));
    }

    @Test
    void documentsIdentifierRulesAndSkuOnlyQuery() {
        JsonNode schemas = openApi.path("components").path("schemas");
        String locationCode = schemas.path("CreateLocationRequest").path("properties").path("code").path("description").asText();
        String sku = schemas.path("EstablishStockRequest").path("properties").path("sku").path("description").asText();
        String from = schemas.path("MoveStockRequest").path("properties").path("from").path("description").asText();

        assertTrue(containsIgnoreCase(locationCode, "case-sensitive"));
        assertTrue(containsIgnoreCase(locationCode, "whitespace") || containsIgnoreCase(locationCode, "trimmed"));
        assertTrue(containsIgnoreCase(sku, "case-sensitive"));
        assertTrue(containsIgnoreCase(sku, "trimmed") || containsIgnoreCase(sku, "whitespace"));
        assertTrue(containsIgnoreCase(from, "case-sensitive"));

        JsonNode getStock = openApi.path("paths").path("/stock").path("get");
        String operationDescription = getStock.path("description").asText();
        assertTrue(containsIgnoreCase(operationDescription, "sku"));
        assertTrue(containsIgnoreCase(operationDescription, "location"));
        assertTrue(containsIgnoreCase(operationDescription, "out of scope")
                || containsIgnoreCase(operationDescription, "not supported")
                || containsIgnoreCase(operationDescription, "only"));

        JsonNode parameters = getStock.path("parameters");
        assertTrue(parameters.isArray());
        assertEquals(1, parameters.size(), "GET /stock must document only the sku query parameter");
        assertEquals("sku", parameters.get(0).path("name").asText());
        assertEquals("query", parameters.get(0).path("in").asText());
        String skuParam = parameters.get(0).path("description").asText();
        assertTrue(containsIgnoreCase(skuParam, "case-sensitive"));
        assertFalse(hasParameterNamed(parameters, "location"));
    }

    private boolean hasParameterNamed(JsonNode parameters, String name) {
        if (!parameters.isArray()) {
            return false;
        }
        for (JsonNode parameter : parameters) {
            if (name.equals(parameter.path("name").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(String text, String fragment) {
        return text.toLowerCase().contains(fragment.toLowerCase());
    }

    private boolean required(JsonNode array, String value) {
        if (!array.isArray()) {
            return false;
        }
        for (JsonNode node : array) {
            if (value.equals(node.asText())) {
                return true;
            }
        }
        return false;
    }
}
