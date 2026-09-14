package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryItemTest {

    private final Sku sku = Sku.of("SKU-100");
    private final LocationCode code = LocationCode.of("PICK-01");

    @Test
    void buildsFromValidValueObjects() {
        InventoryItem item = InventoryItem.of(sku, code, 5);
        assertEquals(sku, item.sku());
        assertEquals(code, item.locationCode());
        assertEquals(5, item.quantity());
        assertEquals(new InventoryKey(sku, code), item.key());
    }

    @Test
    void allowsZeroQuantity() {
        assertEquals(0, InventoryItem.of(sku, code, 0).quantity());
    }

    @Test
    void rejectsNegativeQuantity() {
        assertThrows(ValidationException.class, () -> InventoryItem.of(sku, code, -1));
    }

    @Test
    void withQuantityReplacesValue() {
        InventoryItem item = InventoryItem.of(sku, code, 5).withQuantity(20);
        assertEquals(20, item.quantity());
    }
}
