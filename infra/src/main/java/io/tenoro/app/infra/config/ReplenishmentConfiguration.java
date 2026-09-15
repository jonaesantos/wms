package io.tenoro.app.infra.config;

import io.tenoro.app.domain.port.inbound.InventoryService;
import io.tenoro.app.domain.port.inbound.ReplenishmentService;
import io.tenoro.app.domain.port.outbound.InventoryRepository;
import io.tenoro.app.domain.port.outbound.LocationRepository;
import io.tenoro.app.domain.port.outbound.ReplenishmentRuleRepository;
import io.tenoro.app.domain.port.outbound.ReplenishmentTaskRepository;
import io.tenoro.app.domain.service.ReplenishmentDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.locks.Lock;

/**
 * Wires replenishment onto the shared inventory lock and {@link InventoryService}
 * so confirmation can reuse atomic movement without deadlocking.
 */
@Configuration
public class ReplenishmentConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ReplenishmentService replenishmentService(
            ReplenishmentRuleRepository ruleRepository,
            ReplenishmentTaskRepository taskRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            InventoryService inventoryService,
            Lock inventoryLock,
            Clock clock) {
        return new ReplenishmentDomainService(
                ruleRepository,
                taskRepository,
                locationRepository,
                inventoryRepository,
                inventoryService,
                inventoryLock,
                clock);
    }
}
