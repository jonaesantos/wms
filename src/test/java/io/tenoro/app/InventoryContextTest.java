package io.tenoro.app;

import io.tenoro.app.domain.port.inbound.InventoryService;
import io.tenoro.app.domain.port.inbound.LocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
class InventoryContextTest {

    @Autowired
    private LocationService locationService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired(required = false)
    private Lock inventoryLock;

    @Test
    void resolvesInventoryBeansAndSharedLock() {
        assertNotNull(locationService, "LocationService bean should be wired");
        assertNotNull(inventoryService, "InventoryService bean should be wired");
        assertNotNull(inventoryLock, "Shared inventory Lock bean should be wired");
        assertInstanceOf(ReentrantLock.class, inventoryLock);
    }
}
