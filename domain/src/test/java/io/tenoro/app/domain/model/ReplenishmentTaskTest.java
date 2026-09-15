package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReplenishmentTaskTest {

    private final Sku sku = Sku.of("SKU-100");
    private final LocationCode from = LocationCode.of("RSV-01");
    private final LocationCode to = LocationCode.of("PICK-01");
    private final Instant now = Instant.parse("2026-09-15T12:00:00Z");

    @Test
    void openTaskGeneratesUuidAndOpenStatus() {
        ReplenishmentTask task = ReplenishmentTask.open(sku, from, to, 60, now);
        assertNotNull(task.id().value());
        assertEquals(sku, task.sku());
        assertEquals(from, task.fromLocation());
        assertEquals(to, task.toLocation());
        assertEquals(60, task.quantity());
        assertEquals(ReplenishmentTaskStatus.OPEN, task.status());
        assertEquals(now, task.createdAt());
        assertEquals(now, task.updatedAt());
    }

    @Test
    void generatedIdsAreDistinctUuids() {
        ReplenishmentTask first = ReplenishmentTask.open(sku, from, to, 10, now);
        ReplenishmentTask second = ReplenishmentTask.open(sku, from, to, 10, now);
        assertNotEquals(first.id(), second.id());
        assertDoesNotThrow(() -> UUID.fromString(first.id().toString()));
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThrows(ValidationException.class, () -> ReplenishmentTask.open(sku, from, to, 0, now));
        assertThrows(ValidationException.class, () -> ReplenishmentTask.open(sku, from, to, -1, now));
    }

    @Test
    void parseRejectsMalformedId() {
        assertThrows(ValidationException.class, () -> ReplenishmentTaskId.parse("not-a-uuid"));
        assertThrows(ValidationException.class, () -> ReplenishmentTaskId.parse(" 11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void parseAcceptsWellFormedUuid() {
        UUID uuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        assertEquals(uuid, ReplenishmentTaskId.parse(uuid.toString()).value());
    }

    @Test
    void withStatusUpdatesStatusAndTimestamp() {
        ReplenishmentTask open = ReplenishmentTask.open(sku, from, to, 10, now);
        Instant later = now.plusSeconds(30);
        ReplenishmentTask confirmed = open.withStatus(ReplenishmentTaskStatus.CONFIRMED, later);
        assertEquals(ReplenishmentTaskStatus.CONFIRMED, confirmed.status());
        assertEquals(later, confirmed.updatedAt());
        assertEquals(open.id(), confirmed.id());
        assertEquals(open.createdAt(), confirmed.createdAt());
    }
}
