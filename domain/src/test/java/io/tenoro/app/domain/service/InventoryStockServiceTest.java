package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ErrorCodes;
import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.exception.ValidationException;
import io.tenoro.app.domain.model.InventoryItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Domain tests for absolute stock establishment and SKU query.
 */
class InventoryStockServiceTest {

    private FakeLocationRepository locations;
    private InventoryDomainService service;

    @BeforeEach
    void setUp() {
        locations = new FakeLocationRepository();
        service = new InventoryDomainService(new FakeInventoryRepository(), locations, new ReentrantLock());
        locations.save(io.tenoro.app.domain.model.Location.of("PICK-01", "PICKING"));
        locations.save(io.tenoro.app.domain.model.Location.of("RSV-01", "RESERVE"));
    }

    @Test
    void establishesStockForNewQuant() {
        InventoryItem item = service.establishStock("SKU-100", "PICK-01", 5L);
        assertEquals(5, item.quantity());
        assertEquals("SKU-100", item.sku().value());
        assertEquals("PICK-01", item.locationCode().value());
    }

    @Test
    void replacesRatherThanIncrements() {
        service.establishStock("SKU-100", "PICK-01", 5L);
        InventoryItem item = service.establishStock("SKU-100", "PICK-01", 20L);
        assertEquals(20, item.quantity());
    }

    @Test
    void establishesZeroStock() {
        InventoryItem item = service.establishStock("SKU-100", "PICK-01", 0L);
        assertEquals(0, item.quantity());
    }

    @Test
    void rejectsUnknownLocationWithNotFound() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.establishStock("SKU-100", "NOPE", 5L));
        assertEquals(ErrorCodes.LOCATION_NOT_FOUND, ex.getCode());
    }

    @Test
    void rejectsNegativeQuantity() {
        assertThrows(ValidationException.class, () -> service.establishStock("SKU-100", "PICK-01", -1L));
    }

    @Test
    void rejectsNullQuantity() {
        assertThrows(ValidationException.class, () -> service.establishStock("SKU-100", "PICK-01", null));
    }

    @Test
    void rejectsInvalidSku() {
        assertThrows(ValidationException.class, () -> service.establishStock(" SKU-100", "PICK-01", 5L));
    }

    @Test
    void queriesStockOrderedByLocationAscending() {
        service.establishStock("SKU-100", "RSV-01", 60L);
        service.establishStock("SKU-100", "PICK-01", 5L);

        List<InventoryItem> items = service.findStockBySku("SKU-100");
        assertEquals(List.of("PICK-01", "RSV-01"), items.stream().map(i -> i.locationCode().value()).toList());
    }

    @Test
    void queriesUnknownSkuReturnsEmpty() {
        assertTrue(service.findStockBySku("SKU-999").isEmpty());
    }

    @Test
    void rejectsInvalidSkuQuery() {
        assertThrows(ValidationException.class, () -> service.findStockBySku("  "));
    }
}
