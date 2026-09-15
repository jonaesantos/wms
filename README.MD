# WMS Challenge

Solucion para el challenge de WMS de Tenoro. Modela inventario por ubicacion y gestiona reabasto desde ubicaciones de reserva hacia ubicaciones de picking.

El proyecto usa Spring Boot 3.5.4, Java 24 y Gradle multi-modulo con arquitectura hexagonal.

## Requisitos

- Java 24.
- No requiere base de datos externa.
- Gradle viene incluido con el wrapper `./gradlew`.

Verificar Java:

```shell
java -version
```

## Levantar La App

```shell
./run.sh
```

Puerto custom:

```shell
./run.sh 9090
```

Gradle directo:

```shell
./gradlew bootRun
```

URLs principales:

- API base: `http://localhost:8080/api/templates`
- Swagger UI: `http://localhost:8080/api/templates/documentation`
- Health: `http://localhost:8080/api/templates/actuator/health`

## Correr Tests

```shell
./gradlew test
```

La suite cubre reglas de dominio, integracion HTTP, errores esperados, OpenAPI, seed y escenarios de concurrencia.

## Seed Inicial

Al arrancar la app se carga un escenario in-memory idempotente. No crea tareas de reabasto automaticamente.

Ubicaciones:

- Picking: `PICK-01`, `PICK-02`.
- Reserva: `RSV-01`, `RSV-02`, `RSV-03`.

Reglas:

| SKU | Picking | Min | Max |
|---|---|---:|---:|
| `SKU-100` | `PICK-01` | 20 | 100 |
| `SKU-200` | `PICK-01` | 10 | 50 |
| `SKU-300` | `PICK-02` | 30 | 120 |

Stock inicial:

| SKU | Location | Quantity |
|---|---|---:|
| `SKU-100` | `PICK-01` | 5 |
| `SKU-200` | `PICK-01` | 40 |
| `SKU-300` | `PICK-02` | 10 |
| `SKU-100` | `RSV-01` | 60 |
| `SKU-100` | `RSV-02` | 50 |
| `SKU-300` | `RSV-03` | 70 |

El seed se puede desactivar con:

```shell
./gradlew bootRun --args="--wms.seed.enabled=false"
```

## Probar Con Curl

Definir base:

```shell
BASE=http://localhost:8080/api/templates
```

### Inventario

Listar ubicaciones:

```shell
curl "$BASE/locations"
```

Crear ubicacion:

```shell
curl -X POST "$BASE/locations" -H 'Content-Type: application/json' \
  -d '{"code":"PICK-03","type":"PICKING"}'
```

Cargar stock absoluto:

```shell
curl -X POST "$BASE/stock" -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-999","locationCode":"PICK-03","quantity":12}'
```

Consultar stock por SKU:

```shell
curl "$BASE/stock?sku=SKU-100"
```

Mover stock:

```shell
curl -X POST "$BASE/stock/move" -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-100","from":"RSV-01","to":"PICK-01","quantity":20}'
```

### Reabasto

Crear regla:

```shell
curl -X POST "$BASE/replenishment-rules" -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-999","locationCode":"PICK-03","min":5,"max":30}'
```

Evaluar y generar tareas:

```shell
curl -X POST "$BASE/replenishment/tasks" -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-100","locationCode":"PICK-01"}'
```

Listar tareas:

```shell
curl "$BASE/replenishment/tasks"
```

Confirmar tarea:

```shell
curl -X POST "$BASE/replenishment/tasks/{id}/confirm"
```

Cancelar tarea:

```shell
curl -X POST "$BASE/replenishment/tasks/{id}/cancel"
```

## Endpoints Implementados

Inventario:

- `POST /locations`
- `GET /locations`
- `POST /stock`
- `GET /stock?sku={sku}`
- `POST /stock/move`

Reabasto:

- `POST /replenishment-rules`
- `POST /replenishment/tasks`
- `GET /replenishment/tasks`
- `POST /replenishment/tasks/{id}/confirm`
- `POST /replenishment/tasks/{id}/cancel`

El template original tambien conserva los endpoints de ejemplo `User` y `Hello`.

## Reglas Principales

- El stock se guarda por `sku + locationCode`.
- `POST /stock` establece una cantidad absoluta, no incremental.
- `POST /stock/move` es atomico: descuenta origen y acredita destino como una sola operacion.
- Las reglas de reabasto solo aplican a ubicaciones `PICKING`.
- La evaluacion genera tareas solo si el stock actual esta por debajo del `min`.
- `max` es el nivel objetivo de reabasto, no una capacidad fisica.
- Las tareas siguen la FSM `OPEN -> CONFIRMED` o `OPEN -> CANCELLED`.
- Confirmar una tarea mueve stock; cancelar no mueve stock.
- Los errores esperados devuelven `400`, `404` o `409` con `code`, `message` y `details`.

## Arquitectura

```text
domain/   -> modelos, reglas de negocio, puertos y servicios Java puro
api/      -> DTOs request/response y contratos OpenAPI
infra/    -> controllers, adapters in-memory, configuracion Spring y seed
src/      -> aplicacion Spring Boot y tests de integracion
```

El codigo sigue el estilo hexagonal del template: los controllers dependen de puertos inbound, los servicios de dominio usan puertos outbound y la infraestructura implementa adapters concretos.

La persistencia es in-memory por alcance del challenge; los datos se pierden al reiniciar la app.
