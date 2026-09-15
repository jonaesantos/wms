package io.tenoro.app.infra.seed;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.port.inbound.InventoryService;
import io.tenoro.app.domain.port.inbound.LocationService;
import io.tenoro.app.domain.port.inbound.ReplenishmentService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Loads the mandatory challenge scenario into in-memory repositories.
 * Idempotent within one process: existing locations, rules, and stock are
 * preserved and no tasks are created. State is not persisted across restarts.
 */
@Component
public class WarehouseSeed {

    private final LocationService locationService;
    private final InventoryService inventoryService;
    private final ReplenishmentService replenishmentService;

    public WarehouseSeed(
            LocationService locationService,
            InventoryService inventoryService,
            ReplenishmentService replenishmentService) {
        this.locationService = locationService;
        this.inventoryService = inventoryService;
        this.replenishmentService = replenishmentService;
    }

    public void load() {
        createLocationIfAbsent("PICK-01", "PICKING");
        createLocationIfAbsent("PICK-02", "PICKING");
        createLocationIfAbsent("RSV-01", "RESERVE");
        createLocationIfAbsent("RSV-02", "RESERVE");
        createLocationIfAbsent("RSV-03", "RESERVE");

        establishIfAbsent("SKU-100", "PICK-01", 5);
        establishIfAbsent("SKU-200", "PICK-01", 40);
        establishIfAbsent("SKU-300", "PICK-02", 10);
        establishIfAbsent("SKU-100", "RSV-01", 60);
        establishIfAbsent("SKU-100", "RSV-02", 50);
        establishIfAbsent("SKU-300", "RSV-03", 70);

        createRuleIfAbsent("SKU-100", "PICK-01", 20, 100);
        createRuleIfAbsent("SKU-200", "PICK-01", 10, 50);
        createRuleIfAbsent("SKU-300", "PICK-02", 30, 120);
    }

    private void createLocationIfAbsent(String code, String type) {
        try {
            locationService.createLocation(code, type);
        } catch (ConflictException ignored) {
            // already present; preserve the existing location
        }
    }

    private void createRuleIfAbsent(String sku, String locationCode, long min, long max) {
        try {
            replenishmentService.createRule(sku, locationCode, min, max);
        } catch (ConflictException ignored) {
            // already present; preserve the existing rule
        }
    }

    private void establishIfAbsent(String sku, String locationCode, long quantity) {
        List<InventoryItem> existing = inventoryService.findStockBySku(sku);
        boolean present = existing.stream().anyMatch(item -> item.locationCode().value().equals(locationCode));
        if (!present) {
            inventoryService.establishStock(sku, locationCode, quantity);
        }
    }
}
