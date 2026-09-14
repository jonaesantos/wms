package io.tenoro.app;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.port.inbound.InventoryService;
import io.tenoro.app.domain.port.inbound.LocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent tests against the wired production beans (task 4.5). These verify
 * the location and inventory services share one process-wide lock and preserve
 * the same invariants proven in the domain concurrency tests.
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConcurrentInventoryIntegrationTest {

    @Autowired
    private LocationService locationService;

    @Autowired
    private InventoryService inventoryService;

    @Test
    void concurrentDuplicateLocationCreationLetsExactlyOneWin() throws Exception {
        int threads = 24;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    locationService.createLocation("PICK-01", "PICKING");
                    created.incrementAndGet();
                } catch (ConflictException e) {
                    conflicts.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(1, created.get());
        assertEquals(threads - 1, conflicts.get());
        assertEquals(1, locationService.listLocations().size());
    }

    @Test
    void competingMovementsConserveStockAndNeverGoNegative() throws Exception {
        locationService.createLocation("RSV-01", "RESERVE");
        locationService.createLocation("PICK-01", "PICKING");
        long total = 200L;
        long each = 20L;
        inventoryService.establishStock("SKU-100", "RSV-01", total);

        int threads = 40;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    inventoryService.moveStock("SKU-100", "RSV-01", "PICK-01", each);
                    succeeded.incrementAndGet();
                } catch (ConflictException ignored) {
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        long origin = quantity("SKU-100", "RSV-01");
        long destination = quantity("SKU-100", "PICK-01");
        assertEquals(total / each, succeeded.get());
        assertTrue(origin >= 0 && destination >= 0);
        assertEquals(total, origin + destination, "total stock must be conserved");
        assertEquals(succeeded.get() * each, destination);
    }

    private long quantity(String sku, String location) {
        return inventoryService.findStockBySku(sku).stream()
                .filter(i -> i.locationCode().value().equals(location))
                .mapToLong(InventoryItem::quantity)
                .findFirst().orElse(0L);
    }
}
