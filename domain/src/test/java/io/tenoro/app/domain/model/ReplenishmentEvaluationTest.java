package io.tenoro.app.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReplenishmentEvaluationTest {

    private final Sku sku = Sku.of("SKU-100");
    private final LocationCode picking = LocationCode.of("PICK-01");
    private final ReplenishmentRule rule = ReplenishmentRule.of(sku, picking, 20, 100);

    @Test
    void targetIsMaxAndRequiredIsMaxMinusCurrentWhenBelowMin() {
        ReplenishmentEvaluation evaluation = ReplenishmentEvaluation.of(rule, 5, 95, List.of());
        assertEquals(100, evaluation.targetQuantity());
        assertEquals(rule.max(), evaluation.targetQuantity());
        assertEquals(95, evaluation.requiredQuantity());
        assertEquals(rule.max() - 5, evaluation.requiredQuantity());
        assertEquals(ReplenishmentOutcome.PLANNED, evaluation.outcome());
        assertEquals(0, evaluation.shortfall());
    }

    @Test
    void notRequiredAtMinHasZeroRequiredAndShortfall() {
        ReplenishmentEvaluation evaluation = ReplenishmentEvaluation.of(rule, 20, 0, List.of());
        assertEquals(ReplenishmentOutcome.NOT_REQUIRED, evaluation.outcome());
        assertEquals(0, evaluation.requiredQuantity());
        assertEquals(0, evaluation.shortfall());
        assertTrue(evaluation.tasks().isEmpty());
    }

    @Test
    void notRequiredAboveMin() {
        ReplenishmentEvaluation evaluation = ReplenishmentEvaluation.of(rule, 40, 0, List.of());
        assertEquals(ReplenishmentOutcome.NOT_REQUIRED, evaluation.outcome());
        assertEquals(0, evaluation.requiredQuantity());
    }

    @Test
    void unavailableWhenRequiredButNothingAllocated() {
        ReplenishmentEvaluation evaluation = ReplenishmentEvaluation.of(rule, 5, 0, List.of());
        assertEquals(ReplenishmentOutcome.UNAVAILABLE, evaluation.outcome());
        assertEquals(95, evaluation.requiredQuantity());
        assertEquals(95, evaluation.shortfall());
    }

    @Test
    void partiallyPlannedWhenAllocatedIsBetweenZeroAndRequired() {
        ReplenishmentTask task = ReplenishmentTask.open(
                sku, LocationCode.of("RSV-01"), picking, 70, Instant.parse("2026-09-15T12:00:00Z"));
        ReplenishmentEvaluation evaluation = ReplenishmentEvaluation.of(rule, 5, 70, List.of(task));
        assertEquals(ReplenishmentOutcome.PARTIALLY_PLANNED, evaluation.outcome());
        assertEquals(70, evaluation.allocatedQuantity());
        assertEquals(25, evaluation.shortfall());
        assertEquals(1, evaluation.tasks().size());
    }
}
