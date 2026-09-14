package io.tenoro.app.domain.service;

import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.InventoryKey;
import io.tenoro.app.domain.model.Sku;
import io.tenoro.app.domain.port.outbound.InventoryRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory {@link InventoryRepository} test double for domain tests.
 */
class FakeInventoryRepository implements InventoryRepository {

    private final Map<InventoryKey, InventoryItem> store = new ConcurrentHashMap<>();

    @Override
    public InventoryItem save(InventoryItem item) {
        store.put(item.key(), item);
        return item;
    }

    @Override
    public Optional<InventoryItem> find(InventoryKey key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public List<InventoryItem> findBySku(Sku sku) {
        List<InventoryItem> result = new ArrayList<>();
        for (InventoryItem item : store.values()) {
            if (item.sku().equals(sku)) {
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public void saveBoth(InventoryItem origin, InventoryItem destination) {
        store.put(origin.key(), origin);
        store.put(destination.key(), destination);
    }
}
