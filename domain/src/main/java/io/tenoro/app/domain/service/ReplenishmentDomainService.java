package io.tenoro.app.domain.service;

import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.ErrorCodes;
import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.exception.ValidationException;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.InventoryKey;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.model.LocationCode;
import io.tenoro.app.domain.model.LocationType;
import io.tenoro.app.domain.model.ReplenishmentEvaluation;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.model.ReplenishmentTask;
import io.tenoro.app.domain.model.ReplenishmentTaskId;
import io.tenoro.app.domain.model.ReplenishmentTaskStatus;
import io.tenoro.app.domain.model.Sku;
import io.tenoro.app.domain.port.inbound.InventoryService;
import io.tenoro.app.domain.port.inbound.ReplenishmentService;
import io.tenoro.app.domain.port.outbound.InventoryRepository;
import io.tenoro.app.domain.port.outbound.LocationRepository;
import io.tenoro.app.domain.port.outbound.ReplenishmentRuleRepository;
import io.tenoro.app.domain.port.outbound.ReplenishmentTaskRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;

/**
 * Business logic for replenishment rules and tasks. Every public operation runs
 * inside the shared inventory critical section so evaluation, assignable-stock
 * computation, confirmation, and cancellation stay atomic with physical stock.
 */
public class ReplenishmentDomainService implements ReplenishmentService {

    private final ReplenishmentRuleRepository ruleRepository;
    private final ReplenishmentTaskRepository taskRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final Lock lock;
    private final Clock clock;

    public ReplenishmentDomainService(
            ReplenishmentRuleRepository ruleRepository,
            ReplenishmentTaskRepository taskRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            InventoryService inventoryService,
            Lock lock,
            Clock clock) {
        this.ruleRepository = ruleRepository;
        this.taskRepository = taskRepository;
        this.locationRepository = locationRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
        this.lock = lock;
        this.clock = clock;
    }

    @Override
    public ReplenishmentRule createRule(String sku, String locationCode, Long min, Long max) {
        lock.lock();
        try {
            Sku parsedSku = Sku.of(sku);
            LocationCode parsedLocation = LocationCode.of(locationCode);
            long minValue = requireThreshold("min", min);
            long maxValue = requireThreshold("max", max);
            if (minValue < 0) {
                throw new ValidationException(
                        "min must not be negative",
                        Map.of("field", "min", "value", minValue));
            }
            if (maxValue < 0) {
                throw new ValidationException(
                        "max must not be negative",
                        Map.of("field", "max", "value", maxValue));
            }
            if (minValue > maxValue) {
                throw new ValidationException(
                        "min must be less than or equal to max",
                        Map.of("field", "min", "min", minValue, "max", maxValue));
            }

            Location location = requireLocation(parsedLocation);
            requirePicking(location);

            if (ruleRepository.exists(parsedSku, parsedLocation)) {
                throw new ConflictException(
                        ErrorCodes.RULE_ALREADY_EXISTS,
                        "A replenishment rule already exists for SKU '" + parsedSku.value()
                                + "' at '" + parsedLocation.value() + "'",
                        Map.of("sku", parsedSku.value(), "locationCode", parsedLocation.value()));
            }

            return ruleRepository.save(ReplenishmentRule.of(parsedSku, parsedLocation, minValue, maxValue));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ReplenishmentEvaluation evaluate(String sku, String locationCode) {
        lock.lock();
        try {
            Sku parsedSku = Sku.of(sku);
            LocationCode parsedLocation = LocationCode.of(locationCode);
            Location location = requireLocation(parsedLocation);
            requirePicking(location);
            ReplenishmentRule rule = ruleRepository.find(parsedSku, parsedLocation)
                    .orElseThrow(() -> new NotFoundException(
                            ErrorCodes.RULE_NOT_FOUND,
                            "No replenishment rule exists for SKU '" + parsedSku.value()
                                    + "' at '" + parsedLocation.value() + "'",
                            Map.of("sku", parsedSku.value(), "locationCode", parsedLocation.value())));

            if (hasOpenTasks(parsedSku, parsedLocation)) {
                throw new ConflictException(
                        ErrorCodes.REPLENISHMENT_IN_PROGRESS,
                        "Open replenishment tasks already exist for SKU '" + parsedSku.value()
                                + "' at '" + parsedLocation.value() + "'",
                        Map.of("sku", parsedSku.value(), "locationCode", parsedLocation.value()));
            }

            long currentStock = physicalStock(parsedSku, parsedLocation);
            if (currentStock >= rule.min()) {
                return ReplenishmentEvaluation.of(rule, currentStock, 0L, List.of());
            }

            long required = rule.requiredQuantity(currentStock);
            List<ReplenishmentTask> tasks = allocate(parsedSku, parsedLocation, required);
            long allocated = tasks.stream().mapToLong(ReplenishmentTask::quantity).sum();
            Instant at = clock.instant();
            List<ReplenishmentTask> persisted = new ArrayList<>();
            for (ReplenishmentTask task : tasks) {
                persisted.add(taskRepository.save(new ReplenishmentTask(
                        task.id(), task.sku(), task.fromLocation(), task.toLocation(),
                        task.quantity(), task.status(), at, at)));
            }
            return ReplenishmentEvaluation.of(rule, currentStock, allocated, persisted);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ReplenishmentTask> listTasks() {
        lock.lock();
        try {
            return taskRepository.findAll().stream()
                    .sorted(Comparator
                            .comparing(ReplenishmentTask::createdAt)
                            .thenComparing(task -> task.id().toString()))
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ReplenishmentTask confirm(String id) {
        lock.lock();
        try {
            ReplenishmentTaskId taskId = ReplenishmentTaskId.parse(id);
            ReplenishmentTask task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new NotFoundException(
                            ErrorCodes.TASK_NOT_FOUND,
                            "Replenishment task '" + taskId + "' does not exist",
                            Map.of("id", taskId.toString())));
            requireOpen(task);

            long originStock = physicalStock(task.sku(), task.fromLocation());
            if (originStock < task.quantity()) {
                throw new ConflictException(
                        ErrorCodes.INSUFFICIENT_STOCK,
                        "Insufficient stock at '" + task.fromLocation().value()
                                + "' for SKU '" + task.sku().value() + "'",
                        Map.of("sku", task.sku().value(),
                                "from", task.fromLocation().value(),
                                "available", originStock,
                                "requested", task.quantity()));
            }

            ReplenishmentRule rule = ruleRepository.find(task.sku(), task.toLocation())
                    .orElseThrow(() -> new NotFoundException(
                            ErrorCodes.RULE_NOT_FOUND,
                            "No replenishment rule exists for SKU '" + task.sku().value()
                                    + "' at '" + task.toLocation().value() + "'",
                            Map.of("sku", task.sku().value(), "locationCode", task.toLocation().value())));
            long destinationStock = physicalStock(task.sku(), task.toLocation());
            long projectedDestination;
            try {
                projectedDestination = Math.addExact(destinationStock, task.quantity());
            } catch (ArithmeticException overflow) {
                throw destinationTargetExceeded(task, destinationStock, rule.max());
            }
            if (projectedDestination > rule.max()) {
                throw destinationTargetExceeded(task, destinationStock, rule.max());
            }

            inventoryService.moveStock(
                    task.sku().value(),
                    task.fromLocation().value(),
                    task.toLocation().value(),
                    task.quantity());
            return taskRepository.save(task.withStatus(ReplenishmentTaskStatus.CONFIRMED, clock.instant()));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ReplenishmentTask cancel(String id) {
        lock.lock();
        try {
            ReplenishmentTaskId taskId = ReplenishmentTaskId.parse(id);
            ReplenishmentTask task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new NotFoundException(
                            ErrorCodes.TASK_NOT_FOUND,
                            "Replenishment task '" + taskId + "' does not exist",
                            Map.of("id", taskId.toString())));
            requireOpen(task);
            return taskRepository.save(task.withStatus(ReplenishmentTaskStatus.CANCELLED, clock.instant()));
        } finally {
            lock.unlock();
        }
    }

    long assignableStock(Sku sku, LocationCode reserve) {
        long physical = physicalStock(sku, reserve);
        long openAllocated = taskRepository.findAll().stream()
                .filter(task -> task.status() == ReplenishmentTaskStatus.OPEN)
                .filter(task -> task.sku().equals(sku))
                .filter(task -> task.fromLocation().equals(reserve))
                .mapToLong(ReplenishmentTask::quantity)
                .sum();
        return Math.max(0L, physical - openAllocated);
    }

    private List<ReplenishmentTask> allocate(Sku sku, LocationCode picking, long required) {
        record Candidate(LocationCode code, long assignable) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (InventoryItem item : inventoryRepository.findBySku(sku)) {
            Location location = locationRepository.findByCode(item.locationCode()).orElse(null);
            if (location == null || location.type() != LocationType.RESERVE) {
                continue;
            }
            long assignable = assignableStock(sku, item.locationCode());
            if (assignable > 0) {
                candidates.add(new Candidate(item.locationCode(), assignable));
            }
        }
        candidates.sort(Comparator
                .comparingLong(Candidate::assignable).reversed()
                .thenComparing(candidate -> candidate.code().value()));

        Instant at = clock.instant();
        List<ReplenishmentTask> tasks = new ArrayList<>();
        long remaining = required;
        for (Candidate candidate : candidates) {
            if (remaining <= 0) {
                break;
            }
            long take = Math.min(candidate.assignable(), remaining);
            if (take <= 0) {
                continue;
            }
            tasks.add(ReplenishmentTask.open(sku, candidate.code(), picking, take, at));
            remaining -= take;
        }
        return tasks;
    }

    private boolean hasOpenTasks(Sku sku, LocationCode picking) {
        return taskRepository.findAll().stream()
                .anyMatch(task -> task.status() == ReplenishmentTaskStatus.OPEN
                        && task.sku().equals(sku)
                        && task.toLocation().equals(picking));
    }

    private long physicalStock(Sku sku, LocationCode locationCode) {
        return inventoryRepository.find(new InventoryKey(sku, locationCode))
                .map(InventoryItem::quantity)
                .orElse(0L);
    }

    private Location requireLocation(LocationCode code) {
        return locationRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCodes.LOCATION_NOT_FOUND,
                        "Location '" + code.value() + "' does not exist",
                        Map.of("locationCode", code.value())));
    }

    private void requirePicking(Location location) {
        if (location.type() != LocationType.PICKING) {
            throw new ValidationException(
                    ErrorCodes.LOCATION_NOT_PICKING,
                    "Location '" + location.code().value() + "' is not a picking location",
                    Map.of("locationCode", location.code().value(), "type", location.type().name()));
        }
    }

    private ConflictException destinationTargetExceeded(
            ReplenishmentTask task, long destinationStock, long max) {
        return new ConflictException(
                ErrorCodes.DESTINATION_TARGET_EXCEEDED,
                "Confirming the task would exceed the replenishment target at '"
                        + task.toLocation().value() + "'",
                Map.of("sku", task.sku().value(),
                        "to", task.toLocation().value(),
                        "current", destinationStock,
                        "quantity", task.quantity(),
                        "max", max));
    }

    private void requireOpen(ReplenishmentTask task) {
        if (!task.status().isOpen()) {
            throw new ConflictException(
                    ErrorCodes.TASK_NOT_OPEN,
                    "Replenishment task '" + task.id() + "' is " + task.status() + " and cannot be changed",
                    Map.of("id", task.id().toString(), "status", task.status().name()));
        }
    }

    private long requireThreshold(String field, Long value) {
        if (value == null) {
            throw new ValidationException(field + " must not be null", Map.of("field", field));
        }
        return value;
    }
}
