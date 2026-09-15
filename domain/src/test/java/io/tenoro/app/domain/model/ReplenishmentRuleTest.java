package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReplenishmentRuleTest {

    private final Sku sku = Sku.of("SKU-100");
    private final LocationCode picking = LocationCode.of("PICK-01");

    @Test
    void buildsFromValidValueObjects() {
        ReplenishmentRule rule = ReplenishmentRule.of(sku, picking, 20, 100);
        assertEquals(sku, rule.sku());
        assertEquals(picking, rule.locationCode());
        assertEquals(20, rule.min());
        assertEquals(100, rule.max());
        assertEquals(100, rule.targetQuantity());
    }

    @Test
    void allowsZeroMinAndMax() {
        ReplenishmentRule rule = ReplenishmentRule.of(sku, picking, 0, 0);
        assertEquals(0, rule.min());
        assertEquals(0, rule.max());
    }

    @Test
    void allowsMinEqualToMax() {
        ReplenishmentRule rule = ReplenishmentRule.of(sku, picking, 10, 10);
        assertEquals(10, rule.min());
        assertEquals(10, rule.max());
    }

    @Test
    void rejectsNegativeMin() {
        assertThrows(ValidationException.class, () -> ReplenishmentRule.of(sku, picking, -1, 10));
    }

    @Test
    void rejectsNegativeMax() {
        assertThrows(ValidationException.class, () -> ReplenishmentRule.of(sku, picking, 0, -1));
    }

    @Test
    void rejectsMinGreaterThanMax() {
        ValidationException ex = assertThrows(
                ValidationException.class, () -> ReplenishmentRule.of(sku, picking, 20, 10));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void rejectsNullSku() {
        assertThrows(ValidationException.class, () -> new ReplenishmentRule(null, picking, 0, 10));
    }

    @Test
    void rejectsNullLocationCode() {
        assertThrows(ValidationException.class, () -> new ReplenishmentRule(sku, null, 0, 10));
    }
}
