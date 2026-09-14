package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.ErrorCodes;
import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.exception.ValidationException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.StockMovementResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Domain tests for atomic stock movement: rejection cases (4.1) and the
 * all-or-nothing debit/credit path with overflow rollback (4.2).
 */
class InventoryMovementServiceTest {

    private FakeInventoryRepository inventory;
    private FakeLocationRepository locations;
    private InventoryDomainService service;

    @BeforeEach
    void setUp() {
        inventory = new FakeInventoryRepository();
        locations = new FakeLocationRepository();
        service = new InventoryDomainService(inventory, locations, new ReentrantLock());
        locations.save(Location.of("RSV-01", "RESERVE"));
        locations.save(Location.of("PICK-01", "PICKING"));
    }

    private long qty(String sku, String location) {
        return service.findStockBySku(sku).stream()
                .filter(i -> i.locationCode().value().equals(location))
                .mapToLong(InventoryItem::quantity)
                .findFirst().orElse(0L);
    }

    // ---- 4.1 rejection cases ----

    @Test
    void rejectsSameLocationWith400() {
        service.establishStock("SKU-100", "RSV-01", 60L);
        assertThrows(ValidationException.class,
                () -> service.moveStock("SKU-100", "RSV-01", "RSV-01", 10L));
    }

    @Test
    void rejectsNonPositiveQuantityWith400() {
        assertThrows(ValidationException.class, () -> service.moveStock("SKU-100", "RSV-01", "PICK-01", 0L));
        assertThrows(ValidationException.class, () -> service.moveStock("SKU-100", "RSV-01", "PICK-01", -5L));
    }

    @Test
    void rejectsUnknownOriginWith404() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.moveStock("SKU-100", "NOPE", "PICK-01", 10L));
        assertEquals(ErrorCodes.LOCATION_NOT_FOUND, ex.getCode());
    }

    @Test
    void rejectsUnknownDestinationWith404() {
        assertThrows(NotFoundException.class, () -> service.moveStock("SKU-100", "RSV-01", "NOPE", 10L));
    }

    @Test
    void absentOriginQuantIsInsufficientStock() {
        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.moveStock("SKU-100", "RSV-01", "PICK-01", 10L));
        assertEquals(ErrorCodes.INSUFFICIENT_STOCK, ex.getCode());
        assertEquals(0, qty("SKU-100", "PICK-01"));
    }

    @Test
    void insufficientOriginStockIsRejectedAndPreservesQuants() {
        service.establishStock("SKU-100", "RSV-01", 5L);
        service.establishStock("SKU-100", "PICK-01", 3L);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.moveStock("SKU-100", "RSV-01", "PICK-01", 10L));
        assertEquals(ErrorCodes.INSUFFICIENT_STOCK, ex.getCode());
        assertEquals(5, qty("SKU-100", "RSV-01"));
        assertEquals(3, qty("SKU-100", "PICK-01"));
    }

    // ---- 4.2 all-or-nothing debit/credit ----

    @Test
    void movesStockSuccessfully() {
        service.establishStock("SKU-100", "RSV-01", 60L);
        service.establishStock("SKU-100", "PICK-01", 5L);

        StockMovementResult result = service.moveStock("SKU-100", "RSV-01", "PICK-01", 20L);

        assertEquals(20, result.quantity());
        assertEquals(40, result.origin().quantity());
        assertEquals(25, result.destination().quantity());
        assertEquals(40, qty("SKU-100", "RSV-01"));
        assertEquals(25, qty("SKU-100", "PICK-01"));
    }

    @Test
    void createsDestinationQuantWhenAbsent() {
        service.establishStock("SKU-100", "RSV-01", 60L);

        StockMovementResult result = service.moveStock("SKU-100", "RSV-01", "PICK-01", 20L);

        assertEquals(20, result.destination().quantity());
        assertEquals(20, qty("SKU-100", "PICK-01"));
        assertEquals(40, qty("SKU-100", "RSV-01"));
    }

    @Test
    void overflowIsRejectedAndBothQuantsPreserved() {
        service.establishStock("SKU-100", "RSV-01", 10L);
        service.establishStock("SKU-100", "PICK-01", Long.MAX_VALUE);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.moveStock("SKU-100", "RSV-01", "PICK-01", 5L));
        assertEquals(ErrorCodes.STOCK_OVERFLOW, ex.getCode());
        assertEquals(10, qty("SKU-100", "RSV-01"));
        assertEquals(Long.MAX_VALUE, qty("SKU-100", "PICK-01"));
    }
}
