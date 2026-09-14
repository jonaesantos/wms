package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.ErrorCodes;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.port.inbound.LocationService;
import io.tenoro.app.domain.port.outbound.LocationRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;

/**
 * Business logic for warehouse location management. Every public operation runs
 * inside the shared inventory critical section so uniqueness checks and writes
 * behave atomically within one process.
 */
public class LocationDomainService implements LocationService {

    private final LocationRepository repository;
    private final Lock lock;

    public LocationDomainService(LocationRepository repository, Lock lock) {
        this.repository = repository;
        this.lock = lock;
    }

    @Override
    public Location createLocation(String code, String type) {
        lock.lock();
        try {
            Location location = Location.of(code, type);
            if (repository.existsByCode(location.code())) {
                throw new ConflictException(
                        ErrorCodes.LOCATION_ALREADY_EXISTS,
                        "Location '" + location.code().value() + "' already exists",
                        Map.of("code", location.code().value()));
            }
            return repository.save(location);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Location> listLocations() {
        lock.lock();
        try {
            return repository.findAll().stream()
                    .sorted(Comparator.comparing(location -> location.code().value()))
                    .toList();
        } finally {
            lock.unlock();
        }
    }
}
