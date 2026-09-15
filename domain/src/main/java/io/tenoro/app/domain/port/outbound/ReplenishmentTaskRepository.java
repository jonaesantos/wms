package io.tenoro.app.domain.port.outbound;

import io.tenoro.app.domain.model.ReplenishmentTask;
import io.tenoro.app.domain.model.ReplenishmentTaskId;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for replenishment-task persistence. Ordering is applied at the
 * service boundary.
 */
public interface ReplenishmentTaskRepository {

    ReplenishmentTask save(ReplenishmentTask task);

    Optional<ReplenishmentTask> findById(ReplenishmentTaskId id);

    List<ReplenishmentTask> findAll();
}
