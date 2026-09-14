package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.ErrorCodes;
import io.tenoro.app.domain.exception.ValidationException;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class LocationDomainServiceTest {

    private LocationDomainService service;

    @BeforeEach
    void setUp() {
        service = new LocationDomainService(new FakeLocationRepository(), new ReentrantLock());
    }

    @Test
    void createsPickingAndReserveLocations() {
        Location picking = service.createLocation("PICK-01", "PICKING");
        Location reserve = service.createLocation("RSV-01", "RESERVE");

        assertEquals("PICK-01", picking.code().value());
        assertEquals(LocationType.PICKING, picking.type());
        assertEquals(LocationType.RESERVE, reserve.type());
    }

    @Test
    void rejectsDuplicateCodeWithConflict() {
        service.createLocation("PICK-01", "PICKING");

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.createLocation("PICK-01", "RESERVE"));
        assertEquals(ErrorCodes.LOCATION_ALREADY_EXISTS, ex.getCode());
    }

    @Test
    void treatsCaseAsSignificant() {
        service.createLocation("PICK-01", "PICKING");
        assertDoesNotThrow(() -> service.createLocation("pick-01", "PICKING"));
    }

    @Test
    void rejectsInvalidType() {
        assertThrows(ValidationException.class, () -> service.createLocation("PICK-01", "WAREHOUSE"));
    }

    @Test
    void listsLocationsOrderedByCodeAscending() {
        service.createLocation("RSV-02", "RESERVE");
        service.createLocation("PICK-01", "PICKING");
        service.createLocation("RSV-01", "RESERVE");

        List<String> codes = service.listLocations().stream().map(l -> l.code().value()).toList();
        assertEquals(List.of("PICK-01", "RSV-01", "RSV-02"), codes);
    }

    @Test
    void listsEmptyWhenNoLocations() {
        assertTrue(service.listLocations().isEmpty());
    }

    @Test
    void concurrentDuplicateCreationLetsExactlyOneWin() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    service.createLocation("PICK-01", "PICKING");
                    created.incrementAndGet();
                } catch (ConflictException e) {
                    conflicts.incrementAndGet();
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(1, created.get());
        assertEquals(threads - 1, conflicts.get());
        assertEquals(1, service.listLocations().size());
    }
}
