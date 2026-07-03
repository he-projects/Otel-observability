# OpenTelemetry Observability Stack

A multi-service Spring Boot project demonstrating **observability** with OpenTelemetry, Tempo, Loki, Prometheus, and Grafana.

Two small microservices (`catalog-service` and `order-service`) emit traces, logs, and metrics so you can follow telemetry through the full stack.

---

## What This Project Does

1. **catalog-service** (port 8080) — returns a product catalog.
2. **order-service** (port 8081) — creates orders and calls catalog-service for product details.
3. Every HTTP request automatically produces **traces** (spans).
4. **Logs** are exported to Loki with `trace_id` and `span_id` attached.
5. **Metrics** are scraped from `/actuator/prometheus` by Prometheus.
6. **Grafana** visualizes everything and correlates traces with logs.

---

## Architecture

```
catalog-service ──┐
                  │  OTLP (HTTP :4318)
order-service  ───┼──► OTel Collector ──► Tempo (traces)
                  │         │
                  │         ├──► Loki (logs)
                  │         └──► Prometheus (metrics via :8889)
                  │
                  └── /actuator/prometheus ◄── Prometheus (scrape)
                                              │
                                              ▼
                                           Grafana
                                    (Tempo + Loki + Prometheus)
```

### The Three Observability Signals

| Signal | Backend | Model | Purpose |
|--------|---------|-------|---------|
| Traces | Tempo | Push (OTLP) | Request path across services |
| Logs | Loki | Push (OTLP) | Log lines linked by `trace_id` |
| Metrics | Prometheus | Pull (scrape) | Request counts, latency, JVM stats |

---

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker Desktop (for `host.docker.internal`)

---

## Running the Project

### 1. Start the observability stack

```bash
cd otel-observability
docker compose up -d
```

Wait ~15 seconds for all containers to become healthy.

### 2. Build and run the services

Terminal 1:

```bash
mvn -pl catalog-service spring-boot:run
```

Terminal 2:

```bash
mvn -pl order-service spring-boot:run
```

### 3. Generate traffic

```bash
# List products
curl http://localhost:8080/api/products

# Create an order (order-service → catalog-service)
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 2}'

# Cross-service health check
curl http://localhost:8081/api/orders/health-check
```

### 4. Open Grafana

| Tool | URL | Credentials |
|------|-----|-------------|
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | — |
| Tempo API | http://localhost:3200 | — |

**In Grafana:**

- **Explore → Tempo** — search traces by service name (`order-service` or `catalog-service`)
- **Explore → Loki** — `{service_name="order-service"}`
- **Explore → Prometheus** — `http_server_requests_seconds_count`

---

## Disabling OTel (local dev without Docker)

If the observability stack is not running, add this JVM option:

```
-Dotel.sdk.disabled=true
```

---

## Configuration Reference

### `catalog-service` / `order-service` — `application.yaml`

| Setting | Description |
|---------|-------------|
| `server.port` | Service port (8080 / 8081) |
| `catalog.base-url` | (order-service) Base URL for catalog RestTemplate calls |
| `management.endpoints.web.exposure.include` | Exposed actuator endpoints (health, prometheus) |
| `management.metrics.export.prometheus.step` | Micrometer aggregation interval (5s) |
| `management.tracing.sampling.probability` | Trace sampling rate (1.0 = 100% for learning) |
| `otel.service.name` | Service name attached to all telemetry |
| `otel.exporter.otlp.endpoint` | OTel Collector address (`http://localhost:4318`) |
| `otel.exporter.otlp.protocol` | OTLP transport (`http/protobuf`) |
| `otel.traces.exporter: otlp` | Export traces to the Collector |
| `otel.logs.exporter: otlp` | Export logs to the Collector |
| `otel.metrics.exporter: none` | Metrics via HTTP scrape, not OTLP push |

### `config/otel/otel-collector-config.yaml`

| Section | Description |
|---------|-------------|
| `receivers.otlp` | Accepts telemetry on ports 4317 (gRPC) and 4318 (HTTP) |
| `exporters.otlp/tempo` | Forwards traces to Tempo |
| `exporters.otlphttp/loki` | Forwards logs to Loki |
| `exporters.prometheus` | Exposes metrics on port 8889 |
| `processors.transform/logs` | Copies `trace_id`/`span_id` into log attributes for Grafana correlation |
| `service.pipelines` | Separate pipelines for traces, logs, and metrics |

### `config/tempo/tempo.yaml`

| Setting | Description |
|---------|-------------|
| `server.http_listen_port: 3200` | Query API port for Grafana |
| `distributor.receivers.otlp` | Receives traces from the Collector |
| `storage.trace.backend: local` | Stores traces on the local filesystem (dev only) |
| `storage.trace.wal` | Write-ahead log to prevent trace loss on crash |

### `config/loki/loki-config.yaml`

| Setting | Description |
|---------|-------------|
| `auth_enabled: false` | No authentication (dev only) |
| `schema_config` | TSDB store with schema v13 |
| `limits_config.allow_structured_metadata` | Accepts structured OTLP logs |
| `limits_config.otlp_config` | Indexes `service.name`, `trace_id`, and `span_id` |

### `config/prometheus/prometheus.yml`

| Job | Description |
|-----|-------------|
| `otel-collector` | Scrapes Collector metrics from `:8889` |
| `catalog-service` | Scrapes `/actuator/prometheus` on host:8080 |
| `order-service` | Scrapes `/actuator/prometheus` on host:8081 |

`host.docker.internal` points to the host machine because Spring Boot runs outside Docker.

### `config/grafana/provisioning/datasources/datasources.yaml`

| Datasource | Description |
|------------|-------------|
| Prometheus | Metrics queries |
| Loki | Log queries + link to Tempo (`derivedFields`) |
| Tempo | Trace queries + link to Loki (`tracesToLogsV2`) + Service Map |

### `docker-compose.yml`

| Service | Port | Role |
|---------|------|------|
| loki | 3100 | Log storage |
| tempo | 3200 | Trace storage |
| otel-collector | 4317, 4318, 8889 | Telemetry gateway |
| prometheus | 9090 | Metrics storage |
| grafana | 3000 | Visualization UI |

---

## Maven Dependencies (parent `pom.xml`)

| Dependency | Role |
|------------|------|
| `opentelemetry-spring-boot-starter` | Auto-instrumentation (HTTP, RestTemplate) |
| `opentelemetry-exporter-otlp` | Export traces and logs via OTLP |
| `spring-boot-starter-actuator` | Exposes `/actuator/prometheus` |
| `micrometer-registry-prometheus` | Prometheus metrics format |

---

## Project Structure

```
otel-observability/
├── catalog-service/
├── order-service/
├── config/
│   ├── otel/
│   ├── tempo/
│   ├── loki/
│   ├── prometheus/
│   └── grafana/
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## Example Request Flow

```
POST /api/orders  (order-service)
    │
    ├─ span: order-service POST /api/orders
    │
    └─ GET /api/products/1  (catalog-service)
           │
           └─ span: catalog-service GET /api/products/{id}

Each span  → OTel Collector → Tempo
Each log   → OTel Collector → Loki (with trace_id)
Each metric → Prometheus scrape → Grafana
```

Open a trace for an order in Grafana — you will see the child span from catalog-service, and clicking **Logs** shows the related log lines in Loki.
