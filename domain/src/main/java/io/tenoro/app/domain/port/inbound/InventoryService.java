package io.tenoro.app.domain.port.inbound;

import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.StockMovementResult;

import java.util.List;

/**
 * Inbound port for physical stock operations. Accepts raw request values so all
 * identifier and quantity validation happens inside the domain.
 */
public interface InventoryService {

    /**
     * Establishes the absolute quantity of a SKU at an existing location,
     * replacing any prior value. Zero is permitted.
     *
     * @throws io.tenoro.app.domain.exception.ValidationException if inputs are invalid
     * @throws io.tenoro.app.domain.exception.NotFoundException   if the location does not exist
     */
    InventoryItem establishStock(String sku, String locationCode, Long quantity);

    /**
     * @return only persisted quants for the exact SKU, ordered by location code ascending.
     * @throws io.tenoro.app.domain.exception.ValidationException if the SKU is invalid
     */
    List<InventoryItem> findStockBySku(String sku);

    /**
     * Moves a positive quantity of a SKU between two distinct existing locations
     * as one atomic debit-and-credit.
     *
     * @throws io.tenoro.app.domain.exception.ValidationException if inputs are invalid or endpoints equal
     * @throws io.tenoro.app.domain.exception.NotFoundException   if either location does not exist
     * @throws io.tenoro.app.domain.exception.ConflictException   on insufficient origin stock or destination overflow
     */
    StockMovementResult moveStock(String sku, String from, String to, Long quantity);
}
