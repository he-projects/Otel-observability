# OpenTelemetry Observability Stack

A Spring Boot demo of **distributed observability** — two microservices that emit traces, logs, and metrics through OpenTelemetry. Every API response includes a **traceId** so you can find the exact request in Grafana.

No database, no auth — the focus is on how telemetry flows across HTTP, Feign, and Kafka.

---

## What this project does

The project simulates a small e-commerce flow with two services:

1. **catalog-service** (8080) — holds an in-memory product list (Keyboard, Mouse, Monitor) and exposes a read-only REST API. It also listens to Kafka and updates a demo **reserved inventory** counter when orders are created.

2. **order-service** (8081) — accepts order requests, fetches product details from catalog via **Feign** (sync HTTP), then publishes an **OrderCreatedEvent** to **Kafka** (async). Catalog consumes that event and reserves stock.

3. **Observability** — every step (HTTP, Feign, Kafka produce/consume) creates spans in the same distributed trace. Traces and logs go to Tempo/Loki via the OTel Collector; metrics are scraped by Prometheus. Grafana ties everything together.

```
POST /api/orders (order-service)
  ├─ Feign GET /api/products/{id}  (catalog-service)   ← sync child span
  ├─ Kafka publish order.created.topic                 ← producer span
  └─ Kafka consume (catalog-service)                   ← consumer span (same trace)
        └─ reserve inventory

Apps ──OTLP──► OTel Collector ──► Tempo / Loki
Apps ──scrape──► Prometheus ──► Grafana
```

### Response format

Success responses are wrapped automatically:

```json
{
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": { }
}
```

Errors include `traceId` without the `data` wrapper:

```json
{
  "errorCode": 404,
  "message": "Product not found: 99",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

---

## Technologies

| Technology | Role in this project |
|------------|----------------------|
| **Spring Boot 4** | Runs both microservices, exposes REST APIs and Actuator endpoints |
| **OpenTelemetry** | Auto-instruments HTTP requests; exports traces and logs via OTLP |
| **OpenFeign** | Declarative HTTP client — order-service calls catalog-service with trace context propagated via W3C headers |
| **Kafka** | Async messaging between services; trace context travels in message headers (`traceparent`) |
| **OTel Collector** | Central gateway — receives OTLP from apps and forwards to Tempo (traces), Loki (logs), Prometheus (collector metrics) |
| **Tempo** | Stores traces; search by trace ID in Grafana |
| **Loki** | Stores logs with `trace_id` / `span_id` for correlation |
| **Prometheus** | Scrapes `/actuator/prometheus` from both services every 5 seconds |
| **Grafana** | UI to explore traces, logs, and metrics; datasources are pre-provisioned |

**Versions:** Java 25 · Spring Boot 4.0.6 · Spring Cloud 2025.1.1 · OpenTelemetry 2.27.0

---

## Modules

### commons (shared library)

Not runnable on its own. Imported by both services and provides:

| Package | What it does |
|---------|--------------|
| `config` | `TraceIdResponseAdvice` — wraps every success response as `{traceId, data}` |
| `exception` | `GlobalExceptionHandler` — returns errors with `traceId` |
| `feign` | `FeignOtelInterceptor` — injects trace headers on outbound Feign calls; `FeignWrappedResponseDecoder` — unwraps peer responses |
| `kafka` | `KafkaEventProducer` — adds `traceparent` header; `KafkaRecordInterceptor` — starts consumer spans linked to the producer trace |

### catalog-service (port 8080)

| Layer | Responsibility |
|-------|----------------|
| `ProductController` | `GET /api/products`, `GET /api/products/{id}`, `GET /api/products/inventory/reserved` |
| `ProductService` | In-memory product catalog |
| `OrderCreatedListener` | Kafka consumer on `order.created.topic` |
| `InventoryService` | Updates reserved stock count per product |

### order-service (port 8081)

| Layer | Responsibility |
|-------|----------------|
| `OrderController` | `POST /api/orders`, `GET /api/orders`, `GET /api/orders/health-check` |
| `OrderService` | Order creation logic — parses request, calls Feign, publishes Kafka event |
| `ProductClient` | Feign interface to catalog-service |
| `OrderEventProducer` | Publishes `OrderCreatedEvent` after a successful order |

---

## Configuration

### `application.yaml` (both services)

| Key | Value | Description |
|-----|-------|-------------|
| `server.port` | 8080 / 8081 | Service port |
| `otel.exporter.otlp.endpoint` | `http://localhost:4318` | Where traces and logs are sent |
| `otel.metrics.exporter` | `none` | Metrics use Prometheus scrape, not OTLP push |
| `management.tracing.sampling.probability` | `1.0` | 100% trace sampling (good for learning) |
| `kafka.bootstrap-servers` | `localhost:9094` | Kafka broker address |
| `message-broker.topic.order-created` | `order.created.topic` | Topic for order events |
| `feign.catalog-service-url` | `http://localhost:8080` | Catalog base URL (order-service only) |

Set `kafka.enabled: false` to run without Kafka (order creation still works, but no async inventory update).

### Infrastructure (`docker-compose.yml` + `config/`)

| Service | Port | Config | What it does |
|---------|------|--------|--------------|
| Kafka | 9094 | `docker-compose.yml` | Message broker (KRaft mode, `apache/kafka:3.9.0`) |
| OTel Collector | 4318 | `config/otel/otel-collector-config.yaml` | Receives OTLP, routes traces → Tempo, logs → Loki |
| Tempo | 3200 | `config/tempo/tempo.yaml` | Trace storage and query API |
| Loki | 3100 | `config/loki/loki-config.yaml` | Log storage with trace correlation |
| Prometheus | 9090 | `config/prometheus/prometheus.yml` | Scrapes apps on `host.docker.internal:8080/8081` |
| Grafana | 3000 | `config/grafana/provisioning/` | Pre-configured datasources (Tempo, Loki, Prometheus) |

---

## Run

**Prerequisites:** Java 25, Maven 3.9+, Docker Desktop

Start Docker first, then build and run both services (catalog before order):

```bash
docker compose up -d          # Kafka + observability stack
mvn clean package             # builds commons, then both services

# Terminal 1
mvn -pl catalog-service spring-boot:run

# Terminal 2
mvn -pl order-service spring-boot:run
```

Grafana: http://localhost:3000 (admin / admin)

---

## Example

**1. List products**

```bash
curl -s http://localhost:8080/api/products
```

**2. Create an order** (triggers Feign + Kafka spans)

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 2}'
```

Response:

```json
{
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": {
    "orderId": 1001,
    "product": { "id": 1, "name": "Keyboard", "price": 49.99 },
    "quantity": 2,
    "total": 99.98
  }
}
```

**3. Verify Kafka consumer worked** (wait 1–2 seconds)

```bash
curl -s http://localhost:8080/api/products/inventory/reserved
# → { "traceId": "...", "data": { "1": 2 } }
```

**4. Find the trace in Grafana**

Copy `traceId` from step 2 → Grafana → **Explore → Tempo** → paste trace ID.

You should see one trace with spans for `POST /api/orders`, the Feign call to catalog, and Kafka producer/consumer.
