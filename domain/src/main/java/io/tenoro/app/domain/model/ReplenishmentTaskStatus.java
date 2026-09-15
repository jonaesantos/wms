package io.tenoro.app.domain.model;

/**
 * Finite state of a replenishment task. Only {@code OPEN → CONFIRMED} and
 * {@code OPEN → CANCELLED} are permitted; {@code CONFIRMED} and {@code CANCELLED}
 * are terminal and read-only.
 */
public enum ReplenishmentTaskStatus {
    OPEN,
    CONFIRMED,
    CANCELLED;

    public boolean isOpen() {
        return this == OPEN;
    }

    public boolean isTerminal() {
        return this == CONFIRMED || this == CANCELLED;
    }
}
