package io.tenoro.app;

import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.ReplenishmentEvaluation;
import io.tenoro.app.domain.port.inbound.InventoryService;
import io.tenoro.app.domain.port.inbound.LocationService;
import io.tenoro.app.domain.port.inbound.ReplenishmentService;
import io.tenoro.app.infra.seed.WarehouseSeed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReplenishmentContextAndSeedTest {

    @Autowired
    private ReplenishmentService replenishmentService;

    @Autowired
    private LocationService locationService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private Lock inventoryLock;

    @Autowired
    private WarehouseSeed warehouseSeed;

    @Test
    void wiresReplenishmentOntoTheSharedLock() {
        assertNotNull(replenishmentService);
        assertNotNull(inventoryLock);
        assertInstanceOf(ReentrantLock.class, inventoryLock);
    }

    @Test
    void seedIsIdempotentAndCreatesNoTasks() {
        warehouseSeed.load();
        assertEquals(5, locationService.listLocations().size());
        assertEquals(3, inventoryService.findStockBySku("SKU-100").size());
        assertEquals(1, inventoryService.findStockBySku("SKU-200").size());
        assertEquals(2, inventoryService.findStockBySku("SKU-300").size());

        inventoryService.establishStock("SKU-100", "PICK-01", 99L);
        inventoryService.establishStock("SKU-300", "PICK-02", 30L);
        assertRule("SKU-100", "PICK-01", 20, 100);
        assertRule("SKU-200", "PICK-01", 10, 50);
        assertRule("SKU-300", "PICK-02", 30, 120);
        assertTrue(replenishmentService.listTasks().isEmpty());

        warehouseSeed.load();

        assertEquals(5, locationService.listLocations().size());
        assertEquals(3, inventoryService.findStockBySku("SKU-100").size());
        assertEquals(99, inventoryService.findStockBySku("SKU-100").stream()
                .filter(item -> item.locationCode().value().equals("PICK-01"))
                .mapToLong(InventoryItem::quantity)
                .findFirst()
                .orElse(-1));
        assertTrue(replenishmentService.listTasks().isEmpty());
    }

    private void assertRule(String sku, String locationCode, long min, long max) {
        ReplenishmentEvaluation evaluation = replenishmentService.evaluate(sku, locationCode);
        assertEquals(min, evaluation.min());
        assertEquals(max, evaluation.max());
    }
}
