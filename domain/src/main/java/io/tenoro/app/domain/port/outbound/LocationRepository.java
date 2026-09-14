package io.tenoro.app.domain.port.outbound;

import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationCode;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for warehouse location persistence.
 */
public interface LocationRepository {

    /**
     * Persists a location. Callers guarantee uniqueness of the code while
     * holding the shared inventory lock.
     */
    Location save(Location location);

    Optional<Location> findByCode(LocationCode code);

    boolean existsByCode(LocationCode code);

    /**
     * @return all persisted locations; ordering is applied at the service boundary.
     */
    List<Location> findAll();
}
