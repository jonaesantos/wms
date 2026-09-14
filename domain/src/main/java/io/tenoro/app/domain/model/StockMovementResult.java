package io.tenoro.app.domain.model;

/**
 * The observable outcome of a successful stock movement: the SKU, the moved
 * quantity, and the complete resulting origin and destination quants.
 */
public record StockMovementResult(Sku sku, long quantity, InventoryItem origin, InventoryItem destination) {
}
