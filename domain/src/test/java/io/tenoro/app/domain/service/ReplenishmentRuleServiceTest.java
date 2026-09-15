package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.ErrorCodes;
import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.exception.ValidationException;
import io.tenoro.app.domain.model.ReplenishmentRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class ReplenishmentRuleServiceTest {

    private FakeLocationRepository locations;
    private ReplenishmentDomainService service;

    @BeforeEach
    void setUp() {
        locations = new FakeLocationRepository();
        FakeInventoryRepository inventory = new FakeInventoryRepository();
        InventoryDomainService inventoryService = new InventoryDomainService(
                inventory, locations, new ReentrantLock());
        service = new ReplenishmentDomainService(
                new FakeReplenishmentRuleRepository(),
                new FakeReplenishmentTaskRepository(),
                locations,
                inventory,
                inventoryService,
                new ReentrantLock(),
                Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC));
        locations.save(io.tenoro.app.domain.model.Location.of("PICK-01", "PICKING"));
        locations.save(io.tenoro.app.domain.model.Location.of("RSV-01", "RESERVE"));
    }

    @Test
    void createsValidRule() {
        ReplenishmentRule rule = service.createRule("SKU-100", "PICK-01", 20L, 100L);
        assertEquals("SKU-100", rule.sku().value());
        assertEquals("PICK-01", rule.locationCode().value());
        assertEquals(20, rule.min());
        assertEquals(100, rule.max());
    }

    @Test
    void rejectsDuplicateRule() {
        service.createRule("SKU-100", "PICK-01", 20L, 100L);
        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.createRule("SKU-100", "PICK-01", 10L, 50L));
        assertEquals(ErrorCodes.RULE_ALREADY_EXISTS, ex.getCode());
    }

    @Test
    void rejectsNonPickingLocation() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.createRule("SKU-100", "RSV-01", 20L, 100L));
        assertEquals(ErrorCodes.LOCATION_NOT_PICKING, ex.getCode());
    }

    @Test
    void rejectsUnknownLocation() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.createRule("SKU-100", "PICK-99", 20L, 100L));
        assertEquals(ErrorCodes.LOCATION_NOT_FOUND, ex.getCode());
    }

    @Test
    void rejectsMinGreaterThanMax() {
        assertThrows(ValidationException.class, () -> service.createRule("SKU-100", "PICK-01", 50L, 10L));
    }

    @Test
    void rejectsNegativeThreshold() {
        assertThrows(ValidationException.class, () -> service.createRule("SKU-100", "PICK-01", -1L, 10L));
        assertThrows(ValidationException.class, () -> service.createRule("SKU-100", "PICK-01", 0L, -5L));
    }

    @Test
    void rejectsNullThreshold() {
        assertThrows(ValidationException.class, () -> service.createRule("SKU-100", "PICK-01", null, 10L));
        assertThrows(ValidationException.class, () -> service.createRule("SKU-100", "PICK-01", 0L, null));
    }
}
