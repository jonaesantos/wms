package io.tenoro.app.infra.adapter.outbound.persistence;

import io.tenoro.app.domain.model.LocationCode;
import io.tenoro.app.domain.model.ReplenishmentTask;
import io.tenoro.app.domain.model.Sku;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryReplenishmentTaskRepositoryTest {

    private final InMemoryReplenishmentTaskRepository repository = new InMemoryReplenishmentTaskRepository();

    @Test
    void saveFindAndList() {
        Instant now = Instant.parse("2026-09-15T12:00:00Z");
        ReplenishmentTask task = ReplenishmentTask.open(
                Sku.of("SKU-100"), LocationCode.of("RSV-01"), LocationCode.of("PICK-01"), 60, now);
        repository.save(task);
        assertEquals(task, repository.findById(task.id()).orElseThrow());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void emptyAndUnknownId() {
        assertTrue(repository.findAll().isEmpty());
        ReplenishmentTask other = ReplenishmentTask.open(
                Sku.of("SKU-100"), LocationCode.of("RSV-01"), LocationCode.of("PICK-01"), 10,
                Instant.parse("2026-09-15T12:00:00Z"));
        assertTrue(repository.findById(other.id()).isEmpty());
    }
}
