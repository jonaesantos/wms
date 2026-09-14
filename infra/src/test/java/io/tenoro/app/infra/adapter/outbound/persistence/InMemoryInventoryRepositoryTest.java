package io.tenoro.app.infra.adapter.outbound.persistence;

import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.InventoryKey;
import io.tenoro.app.domain.model.LocationCode;
import io.tenoro.app.domain.model.Sku;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryInventoryRepositoryTest {

    private final InMemoryInventoryRepository repository = new InMemoryInventoryRepository();
    private final Sku sku = Sku.of("SKU-100");

    private InventoryItem item(String location, long quantity) {
        return InventoryItem.of(sku, LocationCode.of(location), quantity);
    }

    @Test
    void saveReplacesExistingQuantity() {
        repository.save(item("PICK-01", 5));
        repository.save(item("PICK-01", 20));

        assertEquals(20, repository.find(new InventoryKey(sku, LocationCode.of("PICK-01"))).orElseThrow().quantity());
    }

    @Test
    void persistsZeroQuantity() {
        repository.save(item("PICK-01", 0));

        InventoryItem found = repository.find(new InventoryKey(sku, LocationCode.of("PICK-01"))).orElseThrow();
        assertEquals(0, found.quantity());
    }

    @Test
    void findBySkuReturnsOnlyMatchingSku() {
        repository.save(item("PICK-01", 5));
        repository.save(item("RSV-01", 60));
        repository.save(InventoryItem.of(Sku.of("SKU-200"), LocationCode.of("PICK-01"), 40));

        List<InventoryItem> found = repository.findBySku(sku);
        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(i -> i.sku().equals(sku)));
    }

    @Test
    void findBySkuReturnsEmptyWhenNoneMatch() {
        assertTrue(repository.findBySku(sku).isEmpty());
    }

    @Test
    void saveBothPublishesCompletePair() {
        repository.saveBoth(item("RSV-01", 40), item("PICK-01", 20));

        assertEquals(40, repository.find(new InventoryKey(sku, LocationCode.of("RSV-01"))).orElseThrow().quantity());
        assertEquals(20, repository.find(new InventoryKey(sku, LocationCode.of("PICK-01"))).orElseThrow().quantity());
    }
}
