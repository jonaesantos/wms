package io.tenoro.app.domain.service;

import io.tenoro.app.domain.model.ReplenishmentTask;
import io.tenoro.app.domain.model.ReplenishmentTaskId;
import io.tenoro.app.domain.port.outbound.ReplenishmentTaskRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class FakeReplenishmentTaskRepository implements ReplenishmentTaskRepository {

    private final Map<ReplenishmentTaskId, ReplenishmentTask> store = new ConcurrentHashMap<>();

    @Override
    public ReplenishmentTask save(ReplenishmentTask task) {
        store.put(task.id(), task);
        return task;
    }

    @Override
    public Optional<ReplenishmentTask> findById(ReplenishmentTaskId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<ReplenishmentTask> findAll() {
        return new ArrayList<>(store.values());
    }
}
