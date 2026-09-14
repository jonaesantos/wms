package io.tenoro.app.infra.adapter.outbound.persistence;

import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationCode;
import io.tenoro.app.domain.port.outbound.LocationRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link LocationRepository}. Compound uniqueness checks and writes are
 * serialized by the shared inventory lock held in the domain service, so this
 * adapter only needs a thread-safe backing map.
 */
@Repository
public class InMemoryLocationRepository implements LocationRepository {

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
