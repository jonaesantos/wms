package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.ErrorCodes;
import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.exception.ValidationException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationCode;
import io.tenoro.app.domain.model.ReplenishmentTask;
import io.tenoro.app.domain.model.ReplenishmentTaskStatus;
import io.tenoro.app.domain.model.Sku;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class ReplenishmentConfirmCancelServiceTest {

    private FakeInventoryRepository inventory;
    private InventoryDomainService inventoryService;
    private ReplenishmentDomainService service;
    private final ReentrantLock lock = new ReentrantLock();

    @BeforeEach
    void setUp() {
        FakeLocationRepository locations = new FakeLocationRepository();
        inventory = new FakeInventoryRepository();
        inventoryService = new InventoryDomainService(inventory, locations, lock);
        service = new ReplenishmentDomainService(
                new FakeReplenishmentRuleRepository(),
                new FakeReplenishmentTaskRepository(),
                locations,
                inventory,
                inventoryService,
                lock,
                Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC));
        locations.save(Location.of("PICK-01", "PICKING"));
        locations.save(Location.of("RSV-01", "RESERVE"));
        locations.save(Location.of("RSV-02", "RESERVE"));
        service.createRule("SKU-100", "PICK-01", 20L, 100L);
        inventoryService.establishStock("SKU-100", "PICK-01", 5L);
        inventoryService.establishStock("SKU-100", "RSV-01", 60L);
        inventoryService.establishStock("SKU-100", "RSV-02", 50L);
    }

    @Test
    void confirmMovesStockAndClosesTask() {
        ReplenishmentTask task = service.evaluate("SKU-100", "PICK-01").tasks().getFirst();
        ReplenishmentTask confirmed = service.confirm(task.id().toString());
        assertEquals(ReplenishmentTaskStatus.CONFIRMED, confirmed.status());
        assertEquals(0, physical("RSV-01"));
        assertEquals(65, physical("PICK-01"));
    }

    @Test
    void confirmUnknownUuid() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.confirm("11111111-1111-1111-1111-111111111111"));
        assertEquals(ErrorCodes.TASK_NOT_FOUND, ex.getCode());
    }

    @Test
    void confirmMalformedId() {
        assertThrows(ValidationException.class, () -> service.confirm("not-a-uuid"));
    }

    @Test
    void confirmInsufficientReserveStockLeavesTaskOpen() {
        ReplenishmentTask task = service.evaluate("SKU-100", "PICK-01").tasks().getFirst();
        inventoryService.establishStock("SKU-100", "RSV-01", 1L);
        ConflictException ex = assertThrows(ConflictException.class, () -> service.confirm(task.id().toString()));
        assertEquals(ErrorCodes.INSUFFICIENT_STOCK, ex.getCode());
        assertEquals(ReplenishmentTaskStatus.OPEN, service.listTasks().stream()
                .filter(t -> t.id().equals(task.id())).findFirst().orElseThrow().status());
        assertEquals(1, physical("RSV-01"));
        assertEquals(5, physical("PICK-01"));
    }

    @Test
    void confirmExceedingTargetLeavesTaskOpen() {
        ReplenishmentTask task = service.evaluate("SKU-100", "PICK-01").tasks().getFirst();
        inventoryService.establishStock("SKU-100", "PICK-01", 50L);
        ConflictException ex = assertThrows(ConflictException.class, () -> service.confirm(task.id().toString()));
        assertEquals(ErrorCodes.DESTINATION_TARGET_EXCEEDED, ex.getCode());
        assertEquals(ReplenishmentTaskStatus.OPEN, service.listTasks().stream()
                .filter(t -> t.id().equals(task.id())).findFirst().orElseThrow().status());
        assertEquals(60, physical("RSV-01"));
        assertEquals(50, physical("PICK-01"));
    }

    @Test
    void confirmOverflowingDestinationTargetLeavesTaskOpen() {
        ReplenishmentTask task = service.evaluate("SKU-100", "PICK-01").tasks().getFirst();
        inventoryService.establishStock("SKU-100", "PICK-01", Long.MAX_VALUE);
        ConflictException ex = assertThrows(ConflictException.class, () -> service.confirm(task.id().toString()));
        assertEquals(ErrorCodes.DESTINATION_TARGET_EXCEEDED, ex.getCode());
        assertEquals(ReplenishmentTaskStatus.OPEN, service.listTasks().stream()
                .filter(t -> t.id().equals(task.id())).findFirst().orElseThrow().status());
        assertEquals(60, physical("RSV-01"));
        assertEquals(Long.MAX_VALUE, physical("PICK-01"));
    }

    @Test
    void confirmExactlyToTargetSucceeds() {
        ReplenishmentTask task = service.evaluate("SKU-100", "PICK-01").tasks().getFirst();
        inventoryService.establishStock("SKU-100", "PICK-01", 40L);
        ReplenishmentTask confirmed = service.confirm(task.id().toString());
        assertEquals(ReplenishmentTaskStatus.CONFIRMED, confirmed.status());
        assertEquals(100, physical("PICK-01"));
    }

    @Test
    void confirmTerminalTaskRejected() {
        ReplenishmentTask task = service.evaluate("SKU-100", "PICK-01").tasks().getFirst();
        service.confirm(task.id().toString());
        ConflictException ex = assertThrows(ConflictException.class, () -> service.confirm(task.id().toString()));
        assertEquals(ErrorCodes.TASK_NOT_OPEN, ex.getCode());
    }

    @Test
    void cancelOpenTaskMovesNoStockAndReleasesAssignable() {
        ReplenishmentTask task = service.evaluate("SKU-100", "PICK-01").tasks().getFirst();
        assertEquals(0, service.assignableStock(Sku.of("SKU-100"), LocationCode.of("RSV-01")));
        ReplenishmentTask cancelled = service.cancel(task.id().toString());
        assertEquals(ReplenishmentTaskStatus.CANCELLED, cancelled.status());
        assertEquals(60, physical("RSV-01"));
        assertEquals(5, physical("PICK-01"));
        assertEquals(60, service.assignableStock(Sku.of("SKU-100"), LocationCode.of("RSV-01")));
    }

    @Test
    void cancelUnknownAndMalformedAndTerminal() {
        NotFoundException missing = assertThrows(NotFoundException.class,
                () -> service.cancel(UUID.randomUUID().toString()));
        assertEquals(ErrorCodes.TASK_NOT_FOUND, missing.getCode());
        assertThrows(ValidationException.class, () -> service.cancel("bad"));

        ReplenishmentTask task = service.evaluate("SKU-100", "PICK-01").tasks().getFirst();
        service.cancel(task.id().toString());
        ConflictException ex = assertThrows(ConflictException.class, () -> service.cancel(task.id().toString()));
        assertEquals(ErrorCodes.TASK_NOT_OPEN, ex.getCode());
    }

    private long physical(String location) {
        return inventoryService.findStockBySku("SKU-100").stream()
                .filter(item -> item.locationCode().value().equals(location))
                .mapToLong(InventoryItem::quantity)
                .findFirst()
                .orElse(0L);
    }
}
