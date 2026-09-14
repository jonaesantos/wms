package io.tenoro.app.domain.port.inbound;

import io.tenoro.app.domain.model.Location;

import java.util.List;

/**
 * Inbound port for warehouse location management. Accepts raw request values so
 * all identifier and type validation happens inside the domain.
 */
public interface LocationService {

    /**
     * Creates a location with a unique code and a supported type.
     *
     * @throws io.tenoro.app.domain.exception.ValidationException if code or type are invalid
     * @throws io.tenoro.app.domain.exception.ConflictException   if the code already exists
     */
    Location createLocation(String code, String type);

    /**
     * @return all locations ordered by code ascending.
     */
    List<Location> listLocations();
}
