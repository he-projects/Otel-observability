# OpenTelemetry Observability Stack

A hands-on demo of **distributed observability** with two Spring Boot microservices, OpenTelemetry, Grafana, Tempo, Loki, Prometheus, **OpenFeign**, and **Kafka**.

You will see how a single user request produces traces, logs, and metrics across HTTP, Feign, and Kafka — and how every API response returns a **traceId** you can paste into Grafana.

---

## Who is this for?

Anyone learning:

- OpenTelemetry in Spring Boot 4
- Cross-service tracing (sync HTTP + async messaging)
- The LGTM stack (Loki, Grafana, Tempo, Metrics/Prometheus)
- Returning `traceId` in API responses for support and debugging

No database, no auth — just two small services and a full observability pipeline.

---

## What happens in this project? (business flow)

1. **catalog-service** (8080) holds a small in-memory product list (Keyboard, Mouse, Monitor).
2. **order-service** (8081) creates orders:
   - Calls catalog via **Feign** to get product price
   - Publishes an **OrderCreatedEvent** to Kafka
3. **catalog-service** consumes that Kafka event and updates a demo **reserved inventory** counter.
4. Every step emits **traces** and **logs** exported to Tempo/Loki via the OTel Collector.
5. Every HTTP response includes a **traceId** (success and error).

```
You → POST /api/orders (order-service)
        ├─ Feign GET /api/products/1 (catalog-service)     ← sync span
        ├─ Kafka publish order.created.topic               ← producer span
        └─ Kafka consume order.created.topic (catalog)     ← consumer span (same trace)

All telemetry → OTel Collector → Tempo / Loki / Prometheus → Grafana
```

---

## Response format (important)

**Success** — every controller response is wrapped automatically:

```json
{
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "data": { }
}
```

**Error** — exceptions return `traceId` without the `data` wrapper:

```json
{
  "errorCode": 404,
  "message": "Product not found: 99",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

Copy `traceId` from any curl response and search it in Grafana → Tempo.

---

## Components

| Component | Port | Role |
|-----------|------|------|
| **catalog-service** | 8080 | Product catalog + Kafka consumer (inventory) |
| **order-service** | 8081 | Orders via Feign + Kafka producer |
| **Kafka** | 9094 | Async messaging (`order.created.topic`) |
| **OTel Collector** | 4318 | Telemetry gateway (OTLP HTTP) |
| **Tempo** | 3200 | Trace storage |
| **Loki** | 3100 | Log storage |
| **Prometheus** | 9090 | Metrics storage |
| **Grafana** | 3000 | UI (admin / admin) |

### Observability signals

| Signal | Backend | How |
|--------|---------|-----|
| Traces | Tempo | OTLP push |
| Logs | Loki | OTLP push (`trace_id` / `span_id` attached) |
| Metrics | Prometheus | Scrape `/actuator/prometheus` |

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | **25** |
| Maven | **3.9+** |
| Spring Boot | **4.0.6** (parent POM) |
| Docker Desktop | for Kafka + observability stack |

---

## Quick start (5 minutes)

```bash
# 1. Clone and enter project
cd otel-observability

# 2. Start Docker stack (Kafka + Tempo + Loki + Prometheus + Grafana)
docker compose up -d

# 3. Wait ~20 seconds, then verify Kafka is up
docker ps --filter name=kafka

# 4. Build all modules (commons + services)
mvn clean package

# 5. Run services (two terminals)
mvn -pl catalog-service spring-boot:run
mvn -pl order-service spring-boot:run

# 6. Smoke test
curl -s http://localhost:8080/api/products
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d "{\"productId\": 1, \"quantity\": 2}"
```

Open Grafana: http://localhost:3000 (admin / admin) → Explore → Tempo → paste `traceId` from the order response.

---

## Running from IntelliJ

1. **File → Project Structure → Project** → SDK = Java 25
2. **Maven → Reload Project** (loads `commons`, `catalog-service`, `order-service`)
3. Run `CatalogApplication` and `OrderApplication` main methods
4. Ensure Docker stack is running first (`docker compose up -d`)

VM options (optional, when observability stack is off):

```
-Dotel.sdk.disabled=true
```

---

## Full test workflow

Run in order to verify HTTP → Feign → Kafka → Grafana.

### Step 0 — Health

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8081/actuator/health
```

Expected: `{"status":"UP"}`

### Step 1 — List products (traceId in response)

```bash
curl -s http://localhost:8080/api/products
```

### Step 2 — Feign health check

```bash
curl -s http://localhost:8081/api/orders/health-check
```

Expected `data`: `{ "status": "ok", "catalog": "reachable", "transport": "feign" }`

### Step 3 — Create order (Feign + Kafka)

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d "{\"productId\": 1, \"quantity\": 2}"
```

Save the `traceId` — use it in Grafana Tempo.

Under the hood:

1. `order-service` receives the order
2. Feign calls `GET /api/products/1` on catalog-service
3. `OrderCreatedEvent` is published to Kafka
4. catalog-service consumes it and reserves inventory

### Step 4 — Verify Kafka consumer (inventory)

Wait 1–2 seconds:

```bash
curl -s http://localhost:8080/api/products/inventory/reserved
```

Expected `data`: `{ "1": 2 }`

### Step 5 — Error with traceId

```bash
curl -s http://localhost:8080/api/products/99
```

Expected: `errorCode: 404` and a `traceId`.

### Step 6 — Grafana

| What | Where |
|------|-------|
| Trace by ID | Explore → Tempo → paste traceId |
| Logs | Explore → Loki → `{service_name="order-service"}` |
| Metrics | Explore → Prometheus → `http_server_requests_seconds_count` |
| Trace → Logs | Open a trace → **Logs** tab |

### Step 7 — Second order (optional)

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d "{\"productId\": 3, \"quantity\": 1}"

curl -s http://localhost:8080/api/products/inventory/reserved
```

Expected `data`: `{ "1": 2, "3": 1 }`

### Checklist

| Step | Verifies |
|------|----------|
| 0 | Services running |
| 1 | traceId on success |
| 2 | Feign cross-service call |
| 3 | Full trace (HTTP + Feign + Kafka) |
| 4 | Kafka consumer side effect |
| 5 | traceId on error |
| 6 | Grafana end-to-end |

---

## API reference

### catalog-service — http://localhost:8080

| Method | Path | Body | Description |
|--------|------|------|-------------|
| GET | `/api/products` | — | List all products |
| GET | `/api/products/{id}` | — | Get one product (404 if missing) |
| GET | `/api/products/inventory/reserved` | — | Reserved units per product (Kafka demo) |

### order-service — http://localhost:8081

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/api/orders` | `{"productId": 1, "quantity": 2}` | Create order (`quantity` optional, default 1) |
| GET | `/api/orders` | — | Orders created in this session (in-memory) |
| GET | `/api/orders/health-check` | — | Feign connectivity to catalog |

---

## Project structure

```
otel-observability/
├── commons/                         # Shared library (not runnable)
│   ├── config/TraceIdResponseAdvice.java      # wraps {traceId, data}
│   ├── exception/GlobalExceptionHandler.java  # errors with traceId
│   ├── feign/FeignOtelInterceptor.java        # W3C trace propagation
│   ├── feign/FeignWrappedResponseDecoder.java
│   └── kafka/KafkaRecordInterceptor.java      # consumer spans
├── catalog-service/                 # Port 8080 — catalog + Kafka consumer
├── order-service/                   # Port 8081 — orders + Feign + Kafka producer
├── config/                          # Tempo, Loki, Prometheus, Grafana, OTel Collector
├── docker-compose.yml
└── pom.xml                          # Parent POM (Java 25, Spring Boot 4.0.6)
```

### Module responsibilities

| Module | Responsibility |
|--------|----------------|
| **commons** | traceId wrapping, global errors, Feign/Kafka OTel integration |
| **catalog-service** | Product API, Kafka consumer (`OrderCreatedListener`), inventory demo |
| **order-service** | Order API, Feign client to catalog, Kafka producer |

---

## How traceId propagation works

| Layer | Class | What it does |
|-------|-------|--------------|
| HTTP response | `TraceIdResponseAdvice` | Wraps success as `{traceId, data}` |
| HTTP error | `GlobalExceptionHandler` | Returns `{errorCode, message, traceId}` |
| Feign outbound | `FeignOtelInterceptor` | Injects W3C headers on cross-service calls |
| Feign inbound | `FeignWrappedResponseDecoder` | Unwraps peer `{traceId, data}` responses |
| Kafka produce | `KafkaEventProducer` | Adds `traceparent` header |
| Kafka consume | `KafkaRecordInterceptor` | Starts CONSUMER span linked to producer trace |

---

## Configuration

### application.yaml (both services)

| Key | Value | Purpose |
|-----|-------|---------|
| `kafka.bootstrap-servers` | `localhost:9094` | Kafka broker |
| `kafka.enabled` | `true` (default) | Set `false` to disable Kafka beans |
| `message-broker.topic.order-created` | `order.created.topic` | Kafka topic name |
| `feign.catalog-service-url` | `http://localhost:8080` | order-service only |
| `otel.exporter.otlp.endpoint` | `http://localhost:4318` | OTel Collector |
| `otel.metrics.exporter` | `none` | Metrics via Prometheus scrape |
| `management.tracing.sampling.probability` | `1.0` | 100% traces (learning) |

### docker-compose services

| Service | Image | Port |
|---------|-------|------|
| kafka | `apache/kafka:3.9.0` | 9094 |
| otel-collector | `otel/opentelemetry-collector-contrib` | 4317, 4318, 8889 |
| tempo | `grafana/tempo` | 3200 |
| loki | `grafana/loki` | 3100 |
| prometheus | `prom/prometheus` | 9090 |
| grafana | `grafana/grafana` | 3000 |

---

## Maven stack

| Item | Version |
|------|---------|
| Spring Boot | 4.0.6 |
| Java | 25 |
| Spring Cloud | 2025.1.1 |
| OpenTelemetry starter | 2.27.0 |
| Lombok | 1.18.46 |

Key dependencies: `opentelemetry-spring-boot-starter`, `spring-cloud-starter-openfeign`, `spring-kafka`, `spring-boot-jackson2`, `micrometer-registry-prometheus`.

Build from project root (builds `commons` first, then services):

```bash
mvn clean package
```

---

## Running without full Docker

| Want | Do |
|------|-----|
| No observability stack | Add JVM option `-Dotel.sdk.disabled=true` |
| No Kafka | Set `kafka.enabled: false` in `application.yaml` |
| Only Kafka | `docker compose up -d kafka` |

---

## Troubleshooting

### Kafka: `KAFKA_ZOOKEEPER_CONNECT is required`

Old Confluent container still running. Recreate with the correct image:

```bash
docker compose rm -sf kafka
docker compose up -d kafka
docker logs kafka --tail 10
```

Look for: `Kafka Server started` and `Awaiting socket connections on 0.0.0.0:9094`.

### Kafka connection errors in console

1. Confirm broker is up: `docker ps --filter name=kafka`
2. Start only Kafka: `docker compose up -d kafka`
3. Or disable Kafka: `kafka.enabled: false` in `application.yaml`

Kafka client reconnect logs are suppressed via `logging.level.org.apache.kafka.*` in `application.yaml`.

### Build fails on `commons` (Jackson / Lombok)

- Use Java **25**
- Run `mvn clean package` from **project root** (not a single module)
- Lombok needs JDK 23+ annotation processing (configured in parent `pom.xml`)

### IntelliJ: `JDK isn't specified for module 'commons'`

1. Maven → Reload Project
2. File → Project Structure → Project SDK = 25
3. Modules → `commons` → Module SDK = Project SDK

### No traces in Grafana

1. Is Docker stack running? `docker compose ps`
2. Are services started **after** Docker?
3. Is OTLP disabled? Remove `-Dotel.sdk.disabled=true` if set.

---

## Learning path

1. `GET /api/products` → copy traceId → find in Tempo
2. `POST /api/orders` → see Feign child span in trace
3. Check `/api/products/inventory/reserved` → see Kafka consumer span in same trace
4. `GET /api/products/99` → confirm traceId in error JSON
5. In Grafana trace view → click **Logs** → jump to Loki
