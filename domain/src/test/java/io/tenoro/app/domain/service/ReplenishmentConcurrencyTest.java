package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.ErrorCodes;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.ReplenishmentTask;
import io.tenoro.app.domain.model.ReplenishmentTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class ReplenishmentConcurrencyTest {

    private InventoryDomainService inventoryService;
    private ReplenishmentDomainService service;
    private final ReentrantLock lock = new ReentrantLock();

    @BeforeEach
    void setUp() {
        FakeLocationRepository locations = new FakeLocationRepository();
        FakeInventoryRepository inventory = new FakeInventoryRepository();
        inventoryService = new InventoryDomainService(inventory, locations, lock);
        service = new ReplenishmentDomainService(
                new FakeReplenishmentRuleRepository(),
                new FakeReplenishmentTaskRepository(),
                locations,
                inventory,
                inventoryService,
                lock,
                Clock.systemUTC());
        locations.save(Location.of("PICK-01", "PICKING"));
        locations.save(Location.of("PICK-02", "PICKING"));
        locations.save(Location.of("RSV-01", "RESERVE"));
        service.createRule("SKU-100", "PICK-01", 20L, 100L);
        service.createRule("SKU-100", "PICK-02", 20L, 100L);
        inventoryService.establishStock("SKU-100", "PICK-01", 5L);
        inventoryService.establishStock("SKU-100", "PICK-02", 5L);
        inventoryService.establishStock("SKU-100", "RSV-01", 60L);
    }

    @Test
    void concurrentEvaluationsNeverOverAllocateReserve() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        AtomicInteger inProgress = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            String picking = i % 2 == 0 ? "PICK-01" : "PICK-02";
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return service.evaluate("SKU-100", picking).allocatedQuantity();
                } catch (ConflictException ex) {
                    if (ErrorCodes.REPLENISHMENT_IN_PROGRESS.equals(ex.getCode())) {
                        inProgress.incrementAndGet();
                        return 0L;
                    }
                    throw ex;
                }
            }));
        }
        start.countDown();
        long allocated = 0;
        for (Future<Long> future : futures) {
            allocated += future.get();
        }
        pool.shutdown();

        assertTrue(allocated <= 60, "allocated " + allocated);
        long openFromReserve = service.listTasks().stream()
                .filter(task -> task.status() == ReplenishmentTaskStatus.OPEN)
                .filter(task -> task.fromLocation().value().equals("RSV-01"))
                .mapToLong(ReplenishmentTask::quantity)
                .sum();
        assertTrue(openFromReserve <= 60);
        assertTrue(openFromReserve >= 0);
    }

    @Test
    void concurrentConfirmationsDoNotOversell() throws Exception {
        inventoryService.establishStock("SKU-100", "RSV-01", 100L);
        ReplenishmentTask firstTask = service.evaluate("SKU-100", "PICK-01").tasks().getFirst();
        ReplenishmentTask secondTask = service.evaluate("SKU-100", "PICK-02").tasks().getFirst();
        inventoryService.establishStock("SKU-100", "RSV-01", 50L);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (ReplenishmentTask task : List.of(firstTask, secondTask)) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    service.confirm(task.id().toString());
                    succeeded.incrementAndGet();
                } catch (ConflictException ex) {
                    if (ErrorCodes.INSUFFICIENT_STOCK.equals(ex.getCode())) {
                        insufficient.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        long reserve = qty("RSV-01");
        long pick1 = qty("PICK-01");
        long pick2 = qty("PICK-02");
        assertTrue(reserve >= 0);
        assertEquals(5 + 5 + 50, reserve + pick1 + pick2);
        assertTrue(succeeded.get() >= 1);
        assertEquals(2, succeeded.get() + insufficient.get());
        assertTrue(insufficient.get() >= 1);
    }

    private long qty(String location) {
        return inventoryService.findStockBySku("SKU-100").stream()
                .filter(item -> item.locationCode().value().equals(location))
                .mapToLong(item -> item.quantity())
                .findFirst()
                .orElse(0L);
    }
}
