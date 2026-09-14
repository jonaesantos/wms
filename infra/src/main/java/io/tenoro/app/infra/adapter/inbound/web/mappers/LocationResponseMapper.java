package io.tenoro.app.infra.adapter.inbound.web.mappers;

import io.tenoro.app.api.dto.LocationResponse;
import io.tenoro.app.domain.model.Location;

public final class LocationResponseMapper {

    private LocationResponseMapper() {
    }

    public static LocationResponse fromDomain(Location location) {
        return new LocationResponse(location.code().value(), location.type().name());
    }
}
