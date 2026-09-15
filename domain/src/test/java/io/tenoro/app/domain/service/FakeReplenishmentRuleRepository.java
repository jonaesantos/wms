package io.tenoro.app.domain.service;

import io.tenoro.app.domain.model.LocationCode;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.model.Sku;
import io.tenoro.app.domain.port.outbound.ReplenishmentRuleRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class FakeReplenishmentRuleRepository implements ReplenishmentRuleRepository {

    private final Map<String, ReplenishmentRule> store = new ConcurrentHashMap<>();

    @Override
    public ReplenishmentRule save(ReplenishmentRule rule) {
        store.put(key(rule.sku(), rule.locationCode()), rule);
        return rule;
    }

    @Override
    public Optional<ReplenishmentRule> find(Sku sku, LocationCode locationCode) {
        return Optional.ofNullable(store.get(key(sku, locationCode)));
    }

    @Override
    public boolean exists(Sku sku, LocationCode locationCode) {
        return store.containsKey(key(sku, locationCode));
    }

    @Override
    public List<ReplenishmentRule> findAll() {
        return new ArrayList<>(store.values());
    }

    private static String key(Sku sku, LocationCode locationCode) {
        return sku.value() + "\0" + locationCode.value();
    }
}
