package io.tenoro.app.infra.adapter.outbound.persistence;

import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.InventoryKey;
import io.tenoro.app.domain.model.Sku;
import io.tenoro.app.domain.port.outbound.InventoryRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link InventoryRepository} keyed by the composite {@link InventoryKey}.
 * Movement consistency is guaranteed by the shared inventory lock held in the
 * domain service; {@link #saveBoth} publishes both quants together so no partial
 * pair is exposed.
 */
@Repository
public class InMemoryInventoryRepository implements InventoryRepository {

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
