package io.tenoro.app.domain.port.outbound;

import io.tenoro.app.domain.model.LocationCode;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.model.Sku;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for replenishment-rule persistence keyed by SKU + picking location.
 */
public interface ReplenishmentRuleRepository {

    ReplenishmentRule save(ReplenishmentRule rule);

    Optional<ReplenishmentRule> find(Sku sku, LocationCode locationCode);

    boolean exists(Sku sku, LocationCode locationCode);

    List<ReplenishmentRule> findAll();
}
