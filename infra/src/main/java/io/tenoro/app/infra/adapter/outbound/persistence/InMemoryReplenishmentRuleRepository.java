package io.tenoro.app.infra.adapter.outbound.persistence;

import io.tenoro.app.domain.model.LocationCode;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.model.Sku;
import io.tenoro.app.domain.port.outbound.ReplenishmentRuleRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryReplenishmentRuleRepository implements ReplenishmentRuleRepository {

    private final Map<Key, ReplenishmentRule> store = new ConcurrentHashMap<>();

    @Override
    public ReplenishmentRule save(ReplenishmentRule rule) {
        store.put(new Key(rule.sku(), rule.locationCode()), rule);
        return rule;
    }

    @Override
    public Optional<ReplenishmentRule> find(Sku sku, LocationCode locationCode) {
        return Optional.ofNullable(store.get(new Key(sku, locationCode)));
    }

    @Override
    public boolean exists(Sku sku, LocationCode locationCode) {
        return store.containsKey(new Key(sku, locationCode));
    }

    @Override
    public List<ReplenishmentRule> findAll() {
        return new ArrayList<>(store.values());
    }

    private record Key(Sku sku, LocationCode locationCode) {
        private Key {
            Objects.requireNonNull(sku);
            Objects.requireNonNull(locationCode);
        }
    }
}
