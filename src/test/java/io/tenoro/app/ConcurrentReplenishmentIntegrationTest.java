package io.tenoro.app;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.port.inbound.InventoryService;
import io.tenoro.app.domain.port.inbound.LocationService;
import io.tenoro.app.domain.port.inbound.ReplenishmentService;
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

@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConcurrentReplenishmentIntegrationTest {

    @Autowired
    private LocationService locationService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ReplenishmentService replenishmentService;

    @Test
    void concurrentEvaluationsShareLockAndDoNotOverAllocate() throws Exception {
        locationService.createLocation("PICK-01", "PICKING");
        locationService.createLocation("PICK-02", "PICKING");
        locationService.createLocation("RSV-01", "RESERVE");
        replenishmentService.createRule("SKU-100", "PICK-01", 20L, 100L);
        replenishmentService.createRule("SKU-100", "PICK-02", 20L, 100L);
        inventoryService.establishStock("SKU-100", "PICK-01", 5L);
        inventoryService.establishStock("SKU-100", "PICK-02", 5L);
        inventoryService.establishStock("SKU-100", "RSV-01", 60L);

        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String picking = i % 2 == 0 ? "PICK-01" : "PICK-02";
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return replenishmentService.evaluate("SKU-100", picking).allocatedQuantity();
                } catch (ConflictException ex) {
                    return 0L;
                }
            }));
        }
        start.countDown();
        long allocated = 0;
        for (Future<Long> future : futures) {
            allocated += future.get();
        }
        pool.shutdown();
        assertTrue(allocated <= 60);
    }

    @Test
    void concurrentConfirmationsConservePhysicalStock() throws Exception {
        locationService.createLocation("PICK-01", "PICKING");
        locationService.createLocation("PICK-02", "PICKING");
        locationService.createLocation("RSV-01", "RESERVE");
        replenishmentService.createRule("SKU-100", "PICK-01", 20L, 100L);
        replenishmentService.createRule("SKU-100", "PICK-02", 20L, 100L);
        inventoryService.establishStock("SKU-100", "PICK-01", 5L);
        inventoryService.establishStock("SKU-100", "PICK-02", 5L);
        inventoryService.establishStock("SKU-100", "RSV-01", 100L);
        var first = replenishmentService.evaluate("SKU-100", "PICK-01").tasks().getFirst();
        var second = replenishmentService.evaluate("SKU-100", "PICK-02").tasks().getFirst();
        inventoryService.establishStock("SKU-100", "RSV-01", 50L);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (var task : List.of(first, second)) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    replenishmentService.confirm(task.id().toString());
                    succeeded.incrementAndGet();
                } catch (ConflictException ex) {
                    insufficient.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        long total = qty("RSV-01") + qty("PICK-01") + qty("PICK-02");
        assertEquals(60, total);
        assertTrue(qty("RSV-01") >= 0);
        assertEquals(2, succeeded.get() + insufficient.get());
    }

    private long qty(String location) {
        return inventoryService.findStockBySku("SKU-100").stream()
                .filter(item -> item.locationCode().value().equals(location))
                .mapToLong(InventoryItem::quantity)
                .findFirst()
                .orElse(0L);
    }
}
