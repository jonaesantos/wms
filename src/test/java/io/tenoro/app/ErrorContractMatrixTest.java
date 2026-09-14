package io.tenoro.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract matrix (task 5.1): asserts every specified expected failure returns
 * its exact HTTP status and error code with a message and an object-valued
 * {@code details} map.
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ErrorContractMatrixTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
        location("RSV-01", "RESERVE");
        location("PICK-01", "PICKING");
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

    private void assertError(ResultActions actions, int status, String code) throws Exception {
        actions.andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void validationErrorOnPaddedIdentifier() throws Exception {
        assertError(mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\" PICK-99\",\"type\":\"PICKING\"}")), 400, "VALIDATION_ERROR");
    }

    @Test
    void duplicateLocationConflict() throws Exception {
        assertError(mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"PICK-01\",\"type\":\"PICKING\"}")), 409, "LOCATION_ALREADY_EXISTS");
    }

    @Test
    void locationNotFoundOnStock() throws Exception {
        assertError(mockMvc.perform(post("/stock").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-100\",\"locationCode\":\"NOPE\",\"quantity\":5}")), 404, "LOCATION_NOT_FOUND");
    }

    @Test
    void insufficientStockOnMove() throws Exception {
        stock("SKU-100", "RSV-01", 5);
        assertError(mockMvc.perform(post("/stock/move").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-100\",\"from\":\"RSV-01\",\"to\":\"PICK-01\",\"quantity\":20}")),
                409, "INSUFFICIENT_STOCK");
    }

    @Test
    void overflowOnMove() throws Exception {
        stock("SKU-100", "PICK-01", Long.MAX_VALUE);
        stock("SKU-100", "RSV-01", 10);
        assertError(mockMvc.perform(post("/stock/move").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-100\",\"from\":\"RSV-01\",\"to\":\"PICK-01\",\"quantity\":5}")),
                409, "STOCK_OVERFLOW");
    }

    @Test
    void malformedJsonIsValidationError() throws Exception {
        assertError(mockMvc.perform(post("/stock").contentType(MediaType.APPLICATION_JSON).content("{ not json")),
                400, "VALIDATION_ERROR");
    }
}
