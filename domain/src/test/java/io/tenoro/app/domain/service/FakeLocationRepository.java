package io.tenoro.app.domain.service;

import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationCode;
import io.tenoro.app.domain.port.outbound.LocationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory {@link LocationRepository} test double for domain tests.
 * Intentionally free of any service-level locking so tests observe exactly the
 * concurrency behavior enforced by the domain service.
 */
class FakeLocationRepository implements LocationRepository {

    private final Map<LocationCode, Location> store = new ConcurrentHashMap<>();

    @Override
    public Location save(Location location) {
        store.put(location.code(), location);
        return location;
    }

    @Override
    public Optional<Location> findByCode(LocationCode code) {
        return Optional.ofNullable(store.get(code));
    }

    @Override
    public boolean existsByCode(LocationCode code) {
        return store.containsKey(code);
    }

    @Override
    public List<Location> findAll() {
        return new ArrayList<>(store.values());
    }
}
