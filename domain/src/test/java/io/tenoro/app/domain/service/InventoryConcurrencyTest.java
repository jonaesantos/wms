package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent domain tests (4.3): the shared critical section must keep stock
 * consistent under competing movements, overlapping reads, and replacements.
 */
class InventoryConcurrencyTest {

    private static final long TOTAL = 200L;

    private FakeInventoryRepository inventory;
    private InventoryDomainService service;

    @BeforeEach
    void setUp() {
        inventory = new FakeInventoryRepository();
        FakeLocationRepository locations = new FakeLocationRepository();
        service = new InventoryDomainService(inventory, locations, new ReentrantLock());
        locations.save(Location.of("RSV-01", "RESERVE"));
        locations.save(Location.of("PICK-01", "PICKING"));
        service.establishStock("SKU-100", "RSV-01", TOTAL);
    }

    private long qty(String location) {
        return service.findStockBySku("SKU-100").stream()
                .filter(i -> i.locationCode().value().equals(location))
                .mapToLong(InventoryItem::quantity)
                .findFirst().orElse(0L);
    }

    @Test
    void competingMovementsNeverOversellOrGoNegative() throws Exception {
        int threads = 40;
        long each = 20L; // only TOTAL/each = 10 can succeed
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    service.moveStock("SKU-100", "RSV-01", "PICK-01", each);
                    succeeded.incrementAndGet();
                } catch (ConflictException ignored) {
                    // insufficient stock is the expected loser outcome
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(TOTAL / each, succeeded.get());
        assertTrue(qty("RSV-01") >= 0);
        assertTrue(qty("PICK-01") >= 0);
        assertEquals(TOTAL, qty("RSV-01") + qty("PICK-01"), "total stock must be conserved");
        assertEquals(succeeded.get() * each, qty("PICK-01"));
    }

    @Test
    void overlappingReadsNeverObservePartialPair() throws Exception {
        int movers = 10;
        int readers = 10;
        ExecutorService pool = Executors.newFixedThreadPool(movers + readers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < movers; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    service.moveStock("SKU-100", "RSV-01", "PICK-01", 20L);
                } catch (ConflictException ignored) {
                }
                return null;
            }));
        }
        for (int i = 0; i < readers; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int r = 0; r < 50; r++) {
                    long sum = service.findStockBySku("SKU-100").stream()
                            .mapToLong(InventoryItem::quantity).sum();
                    assertEquals(TOTAL, sum, "read observed a debited origin without credited destination");
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(TOTAL, qty("RSV-01") + qty("PICK-01"));
    }

    @Test
    void concurrentReplacementsLeaveOneCompleteSubmittedValue() throws Exception {
        int threads = 24;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Set<Long> submitted = ConcurrentHashMap.newKeySet();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            long value = 100L + i;
            submitted.add(value);
            futures.add(pool.submit(() -> {
                start.await();
                service.establishStock("SKU-100", "PICK-01", value);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertTrue(submitted.contains(qty("PICK-01")), "final quantity must equal one complete submitted value");
    }
}
