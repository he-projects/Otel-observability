# OpenTelemetry Observability Stack

A multi-service Spring Boot project demonstrating **observability** with OpenTelemetry, Tempo, Loki, Prometheus, Grafana, **OpenFeign**, and **Kafka**.

Every HTTP response includes a **traceId** (success and error). Feign propagates W3C trace context across services, and Kafka messages carry `traceparent` so async hops appear in the same distributed trace.

---

## What This Project Does

| Component | Port | Role |
|-----------|------|------|
| **catalog-service** | 8080 | Product catalog + Kafka consumer (inventory reservation) |
| **order-service** | 8081 | Creates orders via Feign + publishes Kafka events |
| **Kafka** | 9094 | Async messaging between services |
| **OTel Collector** | 4318 | Telemetry gateway (OTLP) |
| **Tempo** | 3200 | Trace storage |
| **Loki** | 3100 | Log storage |
| **Prometheus** | 9090 | Metrics storage |
| **Grafana** | 3000 | Visualization UI |

### Observability signals

| Signal | Backend | How it flows |
|--------|---------|--------------|
| Traces | Tempo | OTLP push via OTel Collector |
| Logs | Loki | OTLP push with `trace_id` / `span_id` |
| Metrics | Prometheus | Pull scrape from `/actuator/prometheus` |

---

## Architecture

```
Client
  │
  ▼
order-service ──Feign (sync)──► catalog-service
  │                                   ▲
  │ Kafka (async)                     │ Kafka consumer span
  └──────── order.created.topic ──────┘

All services ──OTLP :4318──► OTel Collector ──► Tempo / Loki / Prometheus ──► Grafana
```

### End-to-end trace for `POST /api/orders`

```
POST /api/orders                    (order-service — root HTTP span)
  ├─ GET /api/products/{id}       (catalog-service — Feign child span)
  ├─ Kafka produce order.created    (producer span)
  └─ Kafka consume order.created    (catalog-service — CONSUMER span, same traceId)

Response body:
{
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": { "orderId": 1001, "product": {...}, "quantity": 2, "total": 99.98 }
}
```

### Error response (always includes traceId)

```json
{
  "errorCode": 404,
  "message": "Product not found: 99",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

---

## Trace ID in Every Response

Shared module `commons` provides:

| Class | Purpose |
|-------|---------|
| `TraceIdResponseAdvice` | Wraps success bodies as `{ traceId, data }` |
| `GlobalExceptionHandler` | Maps errors to `{ errorCode, message, traceId }` |
| `FeignWrappedResponseDecoder` | Unwraps `{ traceId, data }` on Feign clients |
| `FeignOtelInterceptor` | Injects W3C trace context on outbound Feign calls |
| `KafkaEventProducer` | Adds `traceparent` header on publish |
| `KafkaRecordInterceptor` | Starts CONSUMER span linked to producer trace |

---

## Prerequisites

- Java 25
- Maven 3.9+
- Docker Desktop (for Kafka, Tempo, Loki, Prometheus, Grafana)

---

## Running the Project

### 1. Start infrastructure

```bash
cd otel-observability
docker compose up -d
```

Wait ~20 seconds for Kafka and the observability stack to become ready.

### 2. Build

```bash
mvn clean package
```

### 3. Run services (two terminals)

```bash
mvn -pl catalog-service spring-boot:run
```

```bash
mvn -pl order-service spring-boot:run
```

---

## Test Workflow (step by step)

Run these steps in order to verify the full observability flow: HTTP → Feign → Kafka → traceId in responses → Grafana.

### Step 0 — Check services are up

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8081/actuator/health
```

Expected: `{"status":"UP"}` for both.

---

### Step 1 — Browse catalog (HTTP + traceId)

```bash
curl -s http://localhost:8080/api/products
```

Expected response shape:

```json
{
  "traceId": "<32-char-hex>",
  "data": [
    { "id": 1, "name": "Keyboard", "price": 49.99 },
    { "id": 2, "name": "Mouse", "price": 19.99 },
    { "id": 3, "name": "Monitor", "price": 299.99 }
  ]
}
```

Copy the `traceId` value — you will use it in Grafana (Step 6).

---

### Step 2 — Feign health check (order → catalog)

```bash
curl -s http://localhost:8081/api/orders/health-check
```

Expected:

```json
{
  "traceId": "<hex>",
  "data": { "status": "ok", "catalog": "reachable", "transport": "feign" }
}
```

In Tempo this trace should show two linked spans: `order-service` and `catalog-service`.

---

### Step 3 — Create an order (Feign + Kafka)

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d "{\"productId\": 1, \"quantity\": 2}"
```

Expected:

```json
{
  "traceId": "<hex>",
  "data": {
    "orderId": 1001,
    "product": { "id": 1, "name": "Keyboard", "price": 49.99 },
    "quantity": 2,
    "total": 99.98
  }
}
```

**Save this `traceId`** — it is the main trace to inspect in Grafana.

What happened under the hood:

1. `order-service` received `POST /api/orders`
2. Feign called `GET /api/products/1` on `catalog-service`
3. `OrderCreatedEvent` was published to `order.created.topic`
4. `catalog-service` consumed the event and reserved inventory

---

### Step 4 — Verify Kafka side effect (inventory)

Wait 1–2 seconds, then:

```bash
curl -s http://localhost:8080/api/products/inventory/reserved
```

Expected (after Step 3):

```json
{
  "traceId": "<hex>",
  "data": { "1": 2 }
}
```

Product `1` (Keyboard) now shows `2` reserved units — proof the Kafka consumer ran.

---

### Step 5 — List orders and trigger an error

List orders created in this session:

```bash
curl -s http://localhost:8081/api/orders
```

Request a product that does not exist:

```bash
curl -s http://localhost:8080/api/products/99
```

Expected error (note `traceId` is still present):

```json
{
  "errorCode": 404,
  "message": "Product not found: 99",
  "traceId": "<hex>"
}
```

Same via order-service (Feign propagates the 404):

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d "{\"productId\": 99}"
```

---

### Step 6 — Verify in Grafana

Open http://localhost:3000 (admin / admin).

| Check | Where | What to look for |
|-------|-------|------------------|
| Trace | **Explore → Tempo** | Paste `traceId` from Step 3 |
| Spans | Tempo trace view | `POST /api/orders` → `GET /api/products/1` → Kafka produce → Kafka consume |
| Logs | **Explore → Loki** | `{service_name="order-service"}` or filter by `trace_id` |
| Log correlation | Tempo trace → **Logs** tab | Related log lines in Loki |
| Metrics | **Explore → Prometheus** | `http_server_requests_seconds_count` |

---

### Step 7 — Optional: second order (different product)

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d "{\"productId\": 3, \"quantity\": 1}"

curl -s http://localhost:8080/api/products/inventory/reserved
```

Expected inventory: `{ "1": 2, "3": 1 }` (cumulative from both orders).

---

### Workflow checklist

| Step | Action | Verifies |
|------|--------|----------|
| 0 | Health checks | Services running |
| 1 | `GET /api/products` | traceId in success response |
| 2 | `GET /api/orders/health-check` | Feign cross-service call |
| 3 | `POST /api/orders` | Feign + Kafka + full trace |
| 4 | `GET /api/products/inventory/reserved` | Kafka consumer |
| 5 | `GET /api/products/99` | traceId in error response |
| 6 | Grafana Tempo/Loki | End-to-end observability |

---

## Disabling OTel or Kafka (local dev without Docker)

**OTel** — if the observability stack is not running:

```
-Dotel.sdk.disabled=true
```

**Kafka** — start the broker before the services:

```bash
docker compose up -d kafka
# wait ~15 seconds, then start catalog-service and order-service
```

To disable Kafka integration, set `kafka.enabled=false` in `application.yaml`.

---

## Configuration Reference

### `application.yaml` (both services)

| Setting | Description |
|---------|-------------|
| `kafka.bootstrap-servers` | Kafka broker (`localhost:9094`) |
| `message-broker.topic.order-created` | Topic for async order events |
| `feign.catalog-service-url` | (order-service) Base URL for Feign client |
| `management.tracing.sampling.probability` | `1.0` = 100% sampling for learning |
| `otel.exporter.otlp.endpoint` | OTel Collector (`http://localhost:4318`) |
| `otel.metrics.exporter: none` | Metrics via Prometheus scrape, not OTLP push |

### `docker-compose.yml`

| Service | Port | Role |
|---------|------|------|
| kafka | 9094 | Message broker (KRaft, single node) |
| loki | 3100 | Log storage |
| tempo | 3200 | Trace storage |
| otel-collector | 4317, 4318, 8889 | Telemetry gateway |
| prometheus | 9090 | Metrics |
| grafana | 3000 | UI |

---

## Maven Dependencies (parent `pom.xml`)

| Dependency | Role |
|------------|------|
| `opentelemetry-spring-boot-starter` | Auto-instrumentation (HTTP, Feign, Kafka) |
| `opentelemetry-exporter-otlp` | Export traces and logs via OTLP |
| `spring-cloud-starter-openfeign` | Declarative HTTP clients |
| `spring-kafka` | Kafka producer/consumer |
| `spring-boot-starter-actuator` | `/actuator/prometheus` |
| `micrometer-registry-prometheus` | Prometheus metrics format |

---

## Project Structure

```
otel-observability/
├── commons/                    # Shared: traceId, Feign, Kafka, exceptions
│   └── src/main/java/com/observability/commons/
│       ├── config/TraceIdResponseAdvice.java
│       ├── exception/GlobalExceptionHandler.java
│       ├── feign/FeignOtelInterceptor.java
│       └── kafka/KafkaRecordInterceptor.java
├── catalog-service/
│   ├── consumer/OrderCreatedListener.java
│   └── services/InventoryService.java
├── order-service/
│   ├── feign/ProductClient.java
│   └── producer/OrderEventProducer.java
├── config/                     # Tempo, Loki, Prometheus, Grafana, OTel Collector
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## API Reference

### catalog-service (8080)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/products` | List all products |
| GET | `/api/products/{id}` | Get product by id (404 if missing) |
| GET | `/api/products/inventory/reserved` | Reserved units per product (Kafka demo) |

### order-service (8081)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/orders` | Create order (`productId` required, `quantity` optional) |
| GET | `/api/orders` | List orders from current session |
| GET | `/api/orders/health-check` | Feign connectivity check to catalog |
