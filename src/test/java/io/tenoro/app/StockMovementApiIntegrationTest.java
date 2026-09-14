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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StockMovementApiIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
        createLocation("RSV-01", "RESERVE");
        createLocation("PICK-01", "PICKING");
    }

    private void createLocation(String code, String type) throws Exception {
        mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"type\":\"" + type + "\"}"))
                .andExpect(status().isCreated());
    }

    private void establish(String sku, String location, long quantity) throws Exception {
        mockMvc.perform(post("/stock").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"locationCode\":\"" + location + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private String move(String from, String to, long quantity) {
        return "{\"sku\":\"SKU-100\",\"from\":\"" + from + "\",\"to\":\"" + to + "\",\"quantity\":" + quantity + "}";
    }

    @Test
    void movesStockAndReturnsCompleteResult() throws Exception {
        establish("SKU-100", "RSV-01", 60);
        establish("SKU-100", "PICK-01", 5);

        mockMvc.perform(post("/stock/move").contentType(MediaType.APPLICATION_JSON).content(move("RSV-01", "PICK-01", 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-100"))
                .andExpect(jsonPath("$.quantity").value(20))
                .andExpect(jsonPath("$.from.locationCode").value("RSV-01"))
                .andExpect(jsonPath("$.from.quantity").value(40))
                .andExpect(jsonPath("$.to.locationCode").value("PICK-01"))
                .andExpect(jsonPath("$.to.quantity").value(25));
    }

    @Test
    void createsDestinationQuantWhenAbsent() throws Exception {
        establish("SKU-100", "RSV-01", 60);

        mockMvc.perform(post("/stock/move").contentType(MediaType.APPLICATION_JSON).content(move("RSV-01", "PICK-01", 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to.quantity").value(20));
    }

    @Test
    void insufficientStockReturns409() throws Exception {
        establish("SKU-100", "RSV-01", 5);

        mockMvc.perform(post("/stock/move").contentType(MediaType.APPLICATION_JSON).content(move("RSV-01", "PICK-01", 20)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void absentOriginQuantReturns409() throws Exception {
        mockMvc.perform(post("/stock/move").contentType(MediaType.APPLICATION_JSON).content(move("RSV-01", "PICK-01", 20)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void sameLocationReturns400() throws Exception {
        establish("SKU-100", "RSV-01", 60);

        mockMvc.perform(post("/stock/move").contentType(MediaType.APPLICATION_JSON).content(move("RSV-01", "RSV-01", 20)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nonPositiveQuantityReturns400() throws Exception {
        establish("SKU-100", "RSV-01", 60);

        mockMvc.perform(post("/stock/move").contentType(MediaType.APPLICATION_JSON).content(move("RSV-01", "PICK-01", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownLocationReturns404() throws Exception {
        establish("SKU-100", "RSV-01", 60);

        mockMvc.perform(post("/stock/move").contentType(MediaType.APPLICATION_JSON).content(move("RSV-01", "NOPE", 20)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOCATION_NOT_FOUND"));
    }

    @Test
    void missingQuantityReturns400() throws Exception {
        establish("SKU-100", "RSV-01", 60);

        mockMvc.perform(post("/stock/move").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"from\":\"RSV-01\",\"to\":\"PICK-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void decimalQuantityReturns400() throws Exception {
        establish("SKU-100", "RSV-01", 60);

        mockMvc.perform(post("/stock/move").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"from\":\"RSV-01\",\"to\":\"PICK-01\",\"quantity\":2.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
