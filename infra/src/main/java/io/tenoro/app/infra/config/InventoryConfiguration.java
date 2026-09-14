package io.tenoro.app.infra.config;

import io.tenoro.app.domain.port.inbound.InventoryService;
import io.tenoro.app.domain.port.inbound.LocationService;
import io.tenoro.app.domain.port.outbound.InventoryRepository;
import io.tenoro.app.domain.port.outbound.LocationRepository;
import io.tenoro.app.domain.service.InventoryDomainService;
import io.tenoro.app.domain.service.LocationDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Wires the inventory vertical slice. A single process-wide reentrant lock is
 * shared by the location and inventory services (and, later, replenishment) so
 * compound checks and multi-quant writes are atomic within one process.
 * Reentrancy lets a future replenishment confirmation call the movement
 * operation without deadlocking on the same lock.
 */
@Configuration
public class InventoryConfiguration {

    @Bean
    public Lock inventoryLock() {
        return new ReentrantLock();
    }

    @Bean
    public LocationService locationService(LocationRepository locationRepository, Lock inventoryLock) {
        return new LocationDomainService(locationRepository, inventoryLock);
    }

    @Bean
    public InventoryService inventoryService(
            InventoryRepository inventoryRepository,
            LocationRepository locationRepository,
            Lock inventoryLock) {
        return new InventoryDomainService(inventoryRepository, locationRepository, inventoryLock);
    }
}
