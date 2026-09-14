package io.tenoro.app.infra.adapter.inbound.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.ErrorCodes;
import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.exception.ValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Focused web tests that exercise {@link GlobalExceptionHandler} in isolation
 * via a dummy controller, asserting each domain category and representative
 * deserialization/validation failures map to the specified structured errors.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper strictMapper = new ObjectMapper()
                .configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, false);
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(strictMapper))
                .build();
    }

    @Test
    void mapsValidationExceptionTo400() throws Exception {
        mockMvc.perform(post("/dummy/throw").param("kind", "validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void mapsNotFoundExceptionTo404() throws Exception {
        mockMvc.perform(post("/dummy/throw").param("kind", "notfound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.LOCATION_NOT_FOUND))
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void mapsConflictExceptionTo409() throws Exception {
        mockMvc.perform(post("/dummy/throw").param("kind", "conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INSUFFICIENT_STOCK))
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void mapsMalformedJsonTo400() throws Exception {
        mockMvc.perform(post("/dummy/body").contentType("application/json").content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));
    }

    @Test
    void mapsDecimalForIntegerFieldTo400() throws Exception {
        mockMvc.perform(post("/dummy/body").contentType("application/json").content("{\"quantity\": 5.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));
    }

    @Test
    void mapsOutOfRangeIntegerTo400() throws Exception {
        mockMvc.perform(post("/dummy/body").contentType("application/json")
                        .content("{\"quantity\": 99999999999999999999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));
    }

    @Test
    void mapsMissingRequiredFieldTo400() throws Exception {
        mockMvc.perform(post("/dummy/body").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));
    }

    @RestController
    static class DummyController {

        @PostMapping("/dummy/throw")
        void throwing(@RequestParam String kind) {
            switch (kind) {
                case "validation" -> throw new ValidationException("bad input", Map.of("field", "x"));
                case "notfound" -> throw new NotFoundException(
                        ErrorCodes.LOCATION_NOT_FOUND, "not found", Map.of("locationCode", "X"));
                case "conflict" -> throw new ConflictException(
                        ErrorCodes.INSUFFICIENT_STOCK, "conflict", Map.of("sku", "S"));
                default -> throw new IllegalStateException("unexpected");
            }
        }

        @PostMapping("/dummy/body")
        void body(@Valid @RequestBody DummyBody request) {
            // no-op; validation/deserialization happens before this executes
        }
    }

    record DummyBody(@NotNull Long quantity) {
    }
}
