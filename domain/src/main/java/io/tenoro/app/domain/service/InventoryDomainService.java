package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.ErrorCodes;
import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.exception.ValidationException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.InventoryKey;
import io.tenoro.app.domain.model.LocationCode;
import io.tenoro.app.domain.model.Sku;
import io.tenoro.app.domain.model.StockMovementResult;
import io.tenoro.app.domain.port.inbound.InventoryService;
import io.tenoro.app.domain.port.outbound.InventoryRepository;
import io.tenoro.app.domain.port.outbound.LocationRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;

/**
 * Business logic for physical stock: absolute establishment, SKU query, and
 * atomic movement. Every public operation runs inside the shared inventory
 * critical section so compound read-check-write sequences and multi-quant
 * writes behave atomically within one process.
 */
public class InventoryDomainService implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final Lock lock;

    public InventoryDomainService(
            InventoryRepository inventoryRepository,
            LocationRepository locationRepository,
            Lock lock) {
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
        this.lock = lock;
    }

    @Override
    public InventoryItem establishStock(String sku, String locationCode, Long quantity) {
        lock.lock();
        try {
            Sku parsedSku = Sku.of(sku);
            LocationCode parsedLocation = LocationCode.of(locationCode);
            long value = requireQuantity(quantity);
            if (value < 0) {
                throw new ValidationException(
                        "quantity must not be negative",
                        Map.of("field", "quantity", "value", value));
            }
            requireLocationExists(parsedLocation);

            InventoryItem item = InventoryItem.of(parsedSku, parsedLocation, value);
            return inventoryRepository.save(item);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<InventoryItem> findStockBySku(String sku) {
        lock.lock();
        try {
            Sku parsedSku = Sku.of(sku);
            return inventoryRepository.findBySku(parsedSku).stream()
                    .sorted(Comparator.comparing(item -> item.locationCode().value()))
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public StockMovementResult moveStock(String sku, String from, String to, Long quantity) {
        lock.lock();
        try {
            Sku parsedSku = Sku.of(sku);
            LocationCode fromCode = LocationCode.of(from);
            LocationCode toCode = LocationCode.of(to);
            long amount = requireQuantity(quantity);
            if (amount <= 0) {
                throw new ValidationException(
                        "quantity must be positive",
                        Map.of("field", "quantity", "value", amount));
            }
            if (fromCode.equals(toCode)) {
                throw new ValidationException(
                        "from and to must be different locations",
                        Map.of("from", fromCode.value(), "to", toCode.value()));
            }
            requireLocationExists(fromCode);
            requireLocationExists(toCode);

            long originQuantity = currentQuantity(parsedSku, fromCode);
            long destinationQuantity = currentQuantity(parsedSku, toCode);

            if (originQuantity < amount) {
                throw new ConflictException(
                        ErrorCodes.INSUFFICIENT_STOCK,
                        "Insufficient stock at '" + fromCode.value() + "' for SKU '" + parsedSku.value() + "'",
                        Map.of("sku", parsedSku.value(), "from", fromCode.value(),
                                "available", originQuantity, "requested", amount));
            }

            long newOrigin = Math.subtractExact(originQuantity, amount);
            long newDestination;
            try {
                newDestination = Math.addExact(destinationQuantity, amount);
            } catch (ArithmeticException overflow) {
                throw new ConflictException(
                        ErrorCodes.STOCK_OVERFLOW,
                        "Crediting '" + toCode.value() + "' would overflow the maximum stock value",
                        Map.of("sku", parsedSku.value(), "to", toCode.value(),
                                "current", destinationQuantity, "requested", amount));
            }

            InventoryItem origin = InventoryItem.of(parsedSku, fromCode, newOrigin);
            InventoryItem destination = InventoryItem.of(parsedSku, toCode, newDestination);
            inventoryRepository.saveBoth(origin, destination);

            return new StockMovementResult(parsedSku, amount, origin, destination);
        } finally {
            lock.unlock();
        }
    }

    private long requireQuantity(Long quantity) {
        if (quantity == null) {
            throw new ValidationException("quantity must not be null", Map.of("field", "quantity"));
        }
        return quantity;
    }

    private void requireLocationExists(LocationCode code) {
        if (!locationRepository.existsByCode(code)) {
            throw new NotFoundException(
                    ErrorCodes.LOCATION_NOT_FOUND,
                    "Location '" + code.value() + "' does not exist",
                    Map.of("locationCode", code.value()));
        }
    }

    private long currentQuantity(Sku sku, LocationCode code) {
        return inventoryRepository.find(new InventoryKey(sku, code))
                .map(InventoryItem::quantity)
                .orElse(0L);
    }
}
