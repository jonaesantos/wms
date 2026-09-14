package io.tenoro.app.infra.adapter.inbound.web.mappers;

import io.tenoro.app.api.dto.MoveStockResponse;
import io.tenoro.app.api.dto.StockResponse;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.StockMovementResult;

public final class StockResponseMapper {

    private StockResponseMapper() {
    }

    public static StockResponse fromDomain(InventoryItem item) {
        return new StockResponse(item.sku().value(), item.locationCode().value(), item.quantity());
    }

    public static MoveStockResponse fromMovement(StockMovementResult result) {
        return new MoveStockResponse(
                result.sku().value(),
                result.quantity(),
                fromDomain(result.origin()),
                fromDomain(result.destination()));
    }
}
