package io.tenoro.app.domain.port.outbound;

import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.InventoryKey;
import io.tenoro.app.domain.model.Sku;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for quant (SKU-at-location quantity) persistence.
 */
public interface InventoryRepository {

    /**
     * Upserts a single quant, replacing any existing quantity for its key.
     */
    InventoryItem save(InventoryItem item);

    Optional<InventoryItem> find(InventoryKey key);

    /**
     * @return every persisted quant for the exact SKU; ordering is applied at
     * the service boundary. Absent quants are never fabricated.
     */
    List<InventoryItem> findBySku(Sku sku);

    /**
     * Publishes both sides of a movement as one batch so no intermediate state
     * (a debited origin without a credited destination) is ever observable.
     */
    void saveBoth(InventoryItem origin, InventoryItem destination);
}
