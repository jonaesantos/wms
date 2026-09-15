package io.tenoro.app.infra.adapter.outbound.persistence;

import io.tenoro.app.domain.model.LocationCode;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.model.Sku;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryReplenishmentRuleRepositoryTest {

    private final InMemoryReplenishmentRuleRepository repository = new InMemoryReplenishmentRuleRepository();
    private final Sku sku = Sku.of("SKU-100");
    private final LocationCode picking = LocationCode.of("PICK-01");

    @Test
    void saveAndFindByCompositeKey() {
        ReplenishmentRule rule = ReplenishmentRule.of(sku, picking, 20, 100);
        repository.save(rule);
        assertEquals(rule, repository.find(sku, picking).orElseThrow());
        assertTrue(repository.exists(sku, picking));
    }

    @Test
    void absenceLookup() {
        assertTrue(repository.find(sku, picking).isEmpty());
        assertFalse(repository.exists(sku, picking));
    }

    @Test
    void differentSkuOrLocationAreDistinct() {
        repository.save(ReplenishmentRule.of(sku, picking, 20, 100));
        assertTrue(repository.find(Sku.of("SKU-200"), picking).isEmpty());
        assertTrue(repository.find(sku, LocationCode.of("PICK-02")).isEmpty());
    }
}
