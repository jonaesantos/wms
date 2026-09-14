package io.tenoro.app;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class LocationApiIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
    }

    private String json(String code, String type) throws Exception {
        return objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("code", code);
            put("type", type);
        }});
    }

    @Test
    void createsPickingLocation() throws Exception {
        mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON).content(json("PICK-01", "PICKING")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PICK-01"))
                .andExpect(jsonPath("$.type").value("PICKING"));
    }

    @Test
    void createsReserveLocation() throws Exception {
        mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON).content(json("RSV-01", "RESERVE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RESERVE"));
    }

    @Test
    void rejectsDuplicateWith409() throws Exception {
        mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON).content(json("PICK-01", "PICKING")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON).content(json("PICK-01", "RESERVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCATION_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details.code").value("PICK-01"));
    }

    @Test
    void rejectsInvalidTypeWith400() throws Exception {
        mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON).content(json("PICK-01", "WAREHOUSE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void rejectsPaddedCodeWith400() throws Exception {
        mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON).content(json(" PICK-01", "PICKING")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsBlankCodeWith400() throws Exception {
        mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON).content(json("   ", "PICKING")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listsEmptyWhenNoLocations() throws Exception {
        mockMvc.perform(get("/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listsLocationsOrderedByCodeAscending() throws Exception {
        mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON).content(json("RSV-02", "RESERVE")));
        mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON).content(json("PICK-01", "PICKING")));
        mockMvc.perform(post("/locations").contentType(MediaType.APPLICATION_JSON).content(json("RSV-01", "RESERVE")));

        mockMvc.perform(get("/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].code").value("PICK-01"))
                .andExpect(jsonPath("$[1].code").value("RSV-01"))
                .andExpect(jsonPath("$[2].code").value("RSV-02"));
    }
}
