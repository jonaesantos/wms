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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StockApiIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
        createLocation("PICK-01", "PICKING");
        createLocation("RSV-01", "RESERVE");
    }

    private void createLocation(String code, String type) throws Exception {
        mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"type\":\"" + type + "\"}"))
                .andExpect(status().isCreated());
    }

    private void establish(String body) throws Exception {
        mockMvc.perform(post("/stock").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void establishesStockForNewQuant() throws Exception {
        mockMvc.perform(post("/stock").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\",\"quantity\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-100"))
                .andExpect(jsonPath("$.locationCode").value("PICK-01"))
                .andExpect(jsonPath("$.quantity").value(5));
    }

    @Test
    void replacesExistingStock() throws Exception {
        establish("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\",\"quantity\":5}");
        mockMvc.perform(post("/stock").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\",\"quantity\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(20));
    }

    @Test
    void establishesZeroStock() throws Exception {
        mockMvc.perform(post("/stock").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\",\"quantity\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0));
    }

    @Test
    void rejectsUnknownLocationWith404() throws Exception {
        mockMvc.perform(post("/stock").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"NOPE\",\"quantity\":5}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOCATION_NOT_FOUND"))
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void rejectsNegativeQuantityWith400() throws Exception {
        mockMvc.perform(post("/stock").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\",\"quantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMissingQuantityWith400() throws Exception {
        mockMvc.perform(post("/stock").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsDecimalQuantityWith400() throws Exception {
        mockMvc.perform(post("/stock").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\",\"quantity\":5.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void queriesStockAcrossLocationsOrdered() throws Exception {
        establish("{\"sku\":\"SKU-100\",\"locationCode\":\"RSV-01\",\"quantity\":60}");
        establish("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\",\"quantity\":5}");

        mockMvc.perform(get("/stock").param("sku", "SKU-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].locationCode").value("PICK-01"))
                .andExpect(jsonPath("$[1].locationCode").value("RSV-01"));
    }

    @Test
    void queriesUnknownSkuReturnsEmpty() throws Exception {
        mockMvc.perform(get("/stock").param("sku", "SKU-999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void queryDoesNotFabricateZeroRowsForLocationsWithoutQuant() throws Exception {
        createLocation("PICK-02", "PICKING");
        establish("{\"sku\":\"SKU-100\",\"locationCode\":\"PICK-01\",\"quantity\":5}");

        mockMvc.perform(get("/stock").param("sku", "SKU-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sku").value("SKU-100"))
                .andExpect(jsonPath("$[0].locationCode").value("PICK-01"))
                .andExpect(jsonPath("$[0].quantity").value(5))
                .andExpect(jsonPath("$[*].locationCode", not(hasItem("PICK-02"))));
    }

    @Test
    void queriesMissingSkuParamWith400() throws Exception {
        mockMvc.perform(get("/stock"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void queriesPaddedSkuWith400() throws Exception {
        mockMvc.perform(get("/stock").param("sku", " SKU-100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
