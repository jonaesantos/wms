package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.ErrorCodes;
import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.exception.ValidationException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationCode;
import io.tenoro.app.domain.model.ReplenishmentEvaluation;
import io.tenoro.app.domain.model.ReplenishmentOutcome;
import io.tenoro.app.domain.model.ReplenishmentTask;
import io.tenoro.app.domain.model.ReplenishmentTaskStatus;
import io.tenoro.app.domain.model.Sku;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class ReplenishmentEvaluationServiceTest {

    private FakeLocationRepository locations;
    private FakeInventoryRepository inventory;
    private FakeReplenishmentTaskRepository tasks;
    private InventoryDomainService inventoryService;
    private ReplenishmentDomainService service;
    private final ReentrantLock lock = new ReentrantLock();

    @BeforeEach
    void setUp() {
        locations = new FakeLocationRepository();
        inventory = new FakeInventoryRepository();
        tasks = new FakeReplenishmentTaskRepository();
        inventoryService = new InventoryDomainService(inventory, locations, lock);
        service = new ReplenishmentDomainService(
                new FakeReplenishmentRuleRepository(),
                tasks,
                locations,
                inventory,
                inventoryService,
                lock,
                Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC));
        locations.save(Location.of("PICK-01", "PICKING"));
        locations.save(Location.of("PICK-02", "PICKING"));
        locations.save(Location.of("RSV-01", "RESERVE"));
        locations.save(Location.of("RSV-02", "RESERVE"));
        locations.save(Location.of("RSV-03", "RESERVE"));
    }

    @Test
    void assignableIsPhysicalMinusOpenTasks() {
        inventoryService.establishStock("SKU-100", "RSV-01", 60L);
        service.createRule("SKU-100", "PICK-01", 20L, 100L);
        inventoryService.establishStock("SKU-100", "PICK-01", 5L);
        ReplenishmentEvaluation first = service.evaluate("SKU-100", "PICK-01");
        assertEquals(60, first.tasks().getFirst().quantity());

        long assignable = service.assignableStock(Sku.of("SKU-100"), LocationCode.of("RSV-01"));
        assertEquals(0, assignable);
        assertEquals(60, physical("SKU-100", "RSV-01"));
        assertEquals(5, physical("SKU-100", "PICK-01"));
    }

    @Test
    void assignableClampsToZeroWhenPhysicalBelowOpenAllocated() {
        inventoryService.establishStock("SKU-100", "RSV-01", 50L);
        service.createRule("SKU-100", "PICK-01", 20L, 100L);
        inventoryService.establishStock("SKU-100", "PICK-01", 5L);
        service.evaluate("SKU-100", "PICK-01");
        inventoryService.establishStock("SKU-100", "RSV-01", 30L);

        assertEquals(0, service.assignableStock(Sku.of("SKU-100"), LocationCode.of("RSV-01")));
    }

    @Test
    void belowMinPlansTasksWithTargetAndRequired() {
        inventoryService.establishStock("SKU-100", "PICK-01", 5L);
        inventoryService.establishStock("SKU-100", "RSV-01", 60L);
        inventoryService.establishStock("SKU-100", "RSV-02", 50L);
        service.createRule("SKU-100", "PICK-01", 20L, 100L);

        ReplenishmentEvaluation result = service.evaluate("SKU-100", "PICK-01");
        assertEquals(100, result.targetQuantity());
        assertEquals(95, result.requiredQuantity());
        assertEquals(ReplenishmentOutcome.PLANNED, result.outcome());
        assertEquals(2, result.tasks().size());
        assertEquals("RSV-01", result.tasks().get(0).fromLocation().value());
        assertEquals(60, result.tasks().get(0).quantity());
        assertEquals("RSV-02", result.tasks().get(1).fromLocation().value());
        assertEquals(35, result.tasks().get(1).quantity());
        assertEquals(ReplenishmentTaskStatus.OPEN, result.tasks().get(0).status());
    }

    @Test
    void atMinIsNotRequired() {
        inventoryService.establishStock("SKU-100", "PICK-01", 20L);
        service.createRule("SKU-100", "PICK-01", 20L, 100L);
        ReplenishmentEvaluation result = service.evaluate("SKU-100", "PICK-01");
        assertEquals(ReplenishmentOutcome.NOT_REQUIRED, result.outcome());
        assertEquals(0, result.requiredQuantity());
        assertEquals(0, result.shortfall());
        assertTrue(result.tasks().isEmpty());
    }

    @Test
    void aboveMinIsNotRequired() {
        inventoryService.establishStock("SKU-200", "PICK-01", 40L);
        service.createRule("SKU-200", "PICK-01", 10L, 50L);
        ReplenishmentEvaluation result = service.evaluate("SKU-200", "PICK-01");
        assertEquals(ReplenishmentOutcome.NOT_REQUIRED, result.outcome());
        assertTrue(result.tasks().isEmpty());
    }

    @Test
    void tiesBrokenByAscendingLocationCode() {
        locations.save(Location.of("PICK-T", "PICKING"));
        service.createRule("SKU-T", "PICK-T", 10L, 20L);
        inventoryService.establishStock("SKU-T", "PICK-T", 0L);
        inventoryService.establishStock("SKU-T", "RSV-02", 20L);
        inventoryService.establishStock("SKU-T", "RSV-01", 20L);

        ReplenishmentEvaluation result = service.evaluate("SKU-T", "PICK-T");
        assertEquals(1, result.tasks().size());
        assertEquals("RSV-01", result.tasks().getFirst().fromLocation().value());
        assertEquals(20, result.tasks().getFirst().quantity());
    }

    @Test
    void skipsZeroAssignableReserve() {
        service.createRule("SKU-100", "PICK-01", 20L, 100L);
        inventoryService.establishStock("SKU-100", "PICK-01", 5L);
        inventoryService.establishStock("SKU-100", "RSV-01", 0L);
        inventoryService.establishStock("SKU-100", "RSV-02", 95L);

        ReplenishmentEvaluation result = service.evaluate("SKU-100", "PICK-01");
        assertEquals(1, result.tasks().size());
        assertEquals("RSV-02", result.tasks().getFirst().fromLocation().value());
    }

    @Test
    void partialCoverageIsPartiallyPlanned() {
        service.createRule("SKU-300", "PICK-02", 30L, 120L);
        inventoryService.establishStock("SKU-300", "PICK-02", 10L);
        inventoryService.establishStock("SKU-300", "RSV-03", 70L);

        ReplenishmentEvaluation result = service.evaluate("SKU-300", "PICK-02");
        assertEquals(ReplenishmentOutcome.PARTIALLY_PLANNED, result.outcome());
        assertEquals(70, result.allocatedQuantity());
        assertEquals(40, result.shortfall());
        assertEquals(110, result.requiredQuantity());
    }

    @Test
    void unavailableWhenNothingAssignable() {
        service.createRule("SKU-100", "PICK-01", 20L, 100L);
        inventoryService.establishStock("SKU-100", "PICK-01", 5L);

        ReplenishmentEvaluation result = service.evaluate("SKU-100", "PICK-01");
        assertEquals(ReplenishmentOutcome.UNAVAILABLE, result.outcome());
        assertEquals(0, result.allocatedQuantity());
        assertEquals(95, result.shortfall());
        assertTrue(result.tasks().isEmpty());
    }

    @Test
    void unknownLocationBeforeTypeAndRule() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.evaluate("SKU-100", "NOPE"));
        assertEquals(ErrorCodes.LOCATION_NOT_FOUND, ex.getCode());
    }

    @Test
    void reserveLocationIsNotPickingBeforeMissingRule() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.evaluate("SKU-100", "RSV-01"));
        assertEquals(ErrorCodes.LOCATION_NOT_PICKING, ex.getCode());
    }

    @Test
    void missingRuleAfterPickingLocationExists() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.evaluate("SKU-100", "PICK-01"));
        assertEquals(ErrorCodes.RULE_NOT_FOUND, ex.getCode());
    }

    @Test
    void rejectsEvaluationWhileOpenTasksExist() {
        service.createRule("SKU-100", "PICK-01", 20L, 100L);
        inventoryService.establishStock("SKU-100", "PICK-01", 5L);
        inventoryService.establishStock("SKU-100", "RSV-01", 95L);
        service.evaluate("SKU-100", "PICK-01");

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.evaluate("SKU-100", "PICK-01"));
        assertEquals(ErrorCodes.REPLENISHMENT_IN_PROGRESS, ex.getCode());
        assertEquals(1, service.listTasks().size());
    }

    @Test
    void listsTasksDeterministicallyAndEmpty() {
        assertTrue(service.listTasks().isEmpty());
        service.createRule("SKU-100", "PICK-01", 20L, 100L);
        inventoryService.establishStock("SKU-100", "PICK-01", 5L);
        inventoryService.establishStock("SKU-100", "RSV-01", 60L);
        inventoryService.establishStock("SKU-100", "RSV-02", 50L);
        service.evaluate("SKU-100", "PICK-01");
        List<ReplenishmentTask> listed = service.listTasks();
        assertEquals(2, listed.size());
        assertEquals(listed.stream().map(t -> t.id().toString()).distinct().count(), listed.size());
    }

    private long physical(String sku, String location) {
        return inventoryService.findStockBySku(sku).stream()
                .filter(item -> item.locationCode().value().equals(location))
                .mapToLong(InventoryItem::quantity)
                .findFirst()
                .orElse(0L);
    }
}
