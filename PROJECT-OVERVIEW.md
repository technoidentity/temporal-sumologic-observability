# Spring Boot, Temporal, and Sumo Logic Integration — Project Overview

## 1. Executive Summary

This project is a demonstration application built with **Spring Boot** and the
**Temporal Java SDK**. It runs a simple workflow ("Hello World") on a Temporal worker,
and is instrumented end-to-end for observability: every log line and every metric the
application produces is collected and sent to **Sumo Logic**, a cloud-based observability
platform, using the **Sumo Logic OpenTelemetry Collector**.

The purpose of this document is to explain, in plain language, how the system is built,
why Sumo Logic was selected as the observability backend, and how to set up, run, and
troubleshoot the project.

---

## 2. System Architecture

```
┌──────────────────────┐                  ┌──────────────────────┐
│ springboot-app       │                  │ temporal             │
│ :8080                │───── gRPC ─>─────│ :7233  (gRPC)        │
│ /actuator/prometheus │                  │ :8233  (Web UI)      │
│                      │                  │                      │
└───────────┬──────────┘                  └──────────────────────┘
            │
            │  scrape (15s)
            │
            v
┌───────────┴──────────────────────────────────┐                  ┌────────────────────────┐
│ sumologic-otel-collector                     │───── HTTPS ─>────│ Sumo Logic             │
│                                              │                  │                        │
│ [1] prometheus -> springboot-app:8080        │                  │ * Worker dashboard     │
│     _sourceCategory=springboot-demo          │                  │   (28 panels)          │
│     relabel: namespace -> temporal_namespace │                  │ * Cloud dashboard      │
│                                              │                  │   (43 panels)          │
│ [2] prometheus -> metrics.temporal.io        │                  │ * Logs                 │
│     _sourceCategory=temporal-cloud-metrics   │                  │   (springboot-demo)    │
│     (Bearer auth)                            │                  │                        │
│                                              │                  │                        │
│ [3] file_log -> Docker stdout                │                  │                        │
│     _sourceCategory=springboot-demo          │                  │                        │
└──────────────────────────────────────────────┘                  └────────────────────────┘
```

**Three collector pipelines:**

| # | Receiver | Source | `_sourceCategory` | What it collects |
|---|----------|--------|-------------------|------------------|
| [1] | `prometheus` (springboot-app) | `springboot-app:8080/actuator/prometheus` | `springboot-demo` | Worker SDK metrics (`temporal_*`) — relabels `namespace` → `temporal_namespace` |
| [2] | `prometheus` (temporal-cloud) | `metrics.temporal.io/v1/metrics` | `temporal-cloud-metrics` | Cloud server metrics (`temporal_cloud_v1_*`) — requires `TEMPORAL_CLOUD_METRICS_API_KEY` |
| [3] | `file_log` | Docker container JSON logs | `springboot-demo` | Application logs from `com.example.demo.*` loggers |

**Temporal connection:** The worker connects to the local `temporal` dev server by
default. When `TEMPORAL_API_KEY` is set in `.env`, it connects to Temporal Cloud instead.

---

## 3. Why Sumo Logic Was Chosen

This section explains why logs and metrics are sent to Sumo Logic instead of a
self-hosted observability stack (for example, Grafana with Prometheus) or another
managed vendor such as Datadog.

### 3.1 Key Reasons

- **A single platform for both logs and metrics.** One collector and one backend
  receive application logs and Prometheus-style metrics together, so there is no need
  to run and correlate two separate systems (for example, Loki for logs and
  Prometheus/Grafana for metrics).

- **Built-in OpenTelemetry Collector support.** Sumo Logic provides an official
  collector image with a ready-made exporter, so metric relabeling, batching, and
  export are all configured in a single file, without any custom integration code.

- **No infrastructure to operate.** Sumo Logic is a fully managed cloud service.
  There is no Prometheus server, Grafana instance, or database to install, back up, or
  scale — the collector simply forwards data over a secure HTTPS connection.

- **Dashboards that can be version-controlled and re-used.** Dashboards in this project
  are stored as plain JSON files that can be imported directly into Sumo Logic. This
  means the dashboards live alongside the code in this repository and can be shared or
  re-imported into any Sumo Logic account without rebuilding them manually.

- **Simple routing and filtering by source.** Sumo Logic uses a field called
  `_sourceCategory` to tag where data comes from. This makes it easy to separate worker
  application data from Temporal Cloud server data without needing separate storage
  systems.

- **A capable query language for metrics.** Sumo Logic's metrics query language
  supports aggregation, filtering, and arithmetic between queries (for example,
  dividing a total by a count to calculate an average), which was sufficient to
  reproduce the original Grafana dashboards without needing a separate query engine.

- **Direct integration with Temporal Cloud.** Temporal Cloud publishes its own metrics
  over a standard HTTPS endpoint. The same collector configuration that scrapes the
  application also scrapes Temporal Cloud, so both sources flow into the same platform
  and the same dashboards.

- **Correlated troubleshooting.** Because logs and metrics live in the same platform,
  it is straightforward to investigate a metric spike (for example, a rise in failed
  workflows) alongside the corresponding application log entries, without switching
  between separate tools.

- **Lower setup effort for a demonstration project.** For a project of this scope, using
  a managed service avoids the time and effort of installing and maintaining
  Prometheus, Grafana, Loki, and Alertmanager, while still producing dashboards that
  resemble a production monitoring setup.

### 3.2 Trade-offs to Consider

- Sumo Logic is a **commercial, paid service**. Unlike a self-hosted Prometheus and
  Grafana setup, there are usage-based costs associated with the volume of logs and
  metrics ingested.
- The dashboard JSON files in this repository represent a **snapshot** of each
  dashboard. Editing a JSON file does not automatically update a dashboard that has
  already been imported into Sumo Logic — it must be re-imported, or the changes must
  be applied manually.
- Sumo Logic uses its own metrics query syntax, which is different from PromQL. As a
  result, the original Grafana dashboards for this project required manual translation
  rather than a direct import.

---

## 4. Prerequisites

- Java 17+, Maven 3.8+
- Docker and Docker Compose
- Sumo Logic account with an **OpenTelemetry collector installation token**
- (Optional) Temporal Cloud namespace + API keys for Cloud worker and/or Cloud metrics

---

## 5. Getting Started

### 5.1 Configure Secrets

```bash
cp .env.example .env
# Edit .env — at minimum set SUMOLOGIC_INSTALLATION_TOKEN
```


### 5.2 Build and Run

```bash
mvn clean package
docker compose up --build -d
```

> **Important:** `mvn clean package` must succeed before `docker compose up --build`.
> The Dockerfile copies `target/springboot-sumo-demo-0.0.1-SNAPSHOT.jar`.

### 5.3 Trigger a Workflow

```bash
curl "http://localhost:8080/temporal/hello?name=Sumo"
# → Hello, Sumo!
```

Or open in a browser:

- App: http://localhost:8080/temporal/hello?name=Sumo
- Local Temporal UI: http://localhost:8233 (when using the local dev server)

Task queue: `HELLO_TASK_QUEUE` · Workflow type: `HelloWorkflow`

---

## 6. Configuration Reference

All secrets live in **`.env`** (git-ignored). Both `springboot-app` and
`sumologic-otel-collector` load it via `env_file` in `docker-compose.yml`.

| Variable | Required? | Used by | Purpose |
|----------|-----------|---------|---------|
| `SUMOLOGIC_INSTALLATION_TOKEN` | **Yes** (for Sumo) | Collector | Authenticates the OTel collector with Sumo Logic |
| `TEMPORAL_TARGET` | Cloud worker only | springboot-app | Temporal Cloud endpoint, e.g. `ns.account.tmprl.cloud:7233` |
| `TEMPORAL_NAMESPACE` | Cloud worker + Cloud metrics | App + collector | Temporal namespace name |
| `TEMPORAL_API_KEY` | Cloud worker only | springboot-app | Namespace API key — runs workflows against Temporal Cloud |
| `TEMPORAL_CLOUD_METRICS_API_KEY` | Cloud metrics only | Collector | **Metrics Read-Only** key — scrapes `metrics.temporal.io` |

**You do not need both Temporal API keys for everything:**

| Scenario | Keys needed |
|----------|-------------|
| Local Temporal dev server + Sumo logs/metrics | `SUMOLOGIC_INSTALLATION_TOKEN` only |
| Temporal Cloud worker + worker dashboard | `SUMOLOGIC_*` + `TEMPORAL_TARGET` + `TEMPORAL_NAMESPACE` + `TEMPORAL_API_KEY` |
| Full setup (Cloud worker + Cloud metrics dashboards) | All of the above + `TEMPORAL_CLOUD_METRICS_API_KEY` |

When `TEMPORAL_API_KEY` is **absent**, the worker connects to the local dev server
(`localhost:7233`, namespace `default`) inside the Docker network.

---

## 7. Docker Services

| Service | Port(s) | Role |
|---------|---------|------|
| `springboot-app` | 8080 | Spring Boot app, Temporal worker, `/actuator/prometheus` |
| `temporal` | 7233, 8233 | Local Temporal dev server + Web UI |
| `sumologic-otel-collector` | — | Scrapes metrics, tails container logs, exports to Sumo |

Recreate the collector after changing `otel-collector-config.yaml` or `.env`:

```bash
docker compose up -d --force-recreate sumologic-otel-collector
```

---

## 8. Logging

Workflow and worker logs use loggers under `com.example.demo.*`. The collector ships
Docker stdout logs with `_sourceCategory=springboot-demo`.

Example log searches:

```
HelloWorkflow started
HelloActivity composing greeting
Temporal worker started
Connecting to Temporal Cloud
```

---

## 9. Metrics

### 9.1 Worker / SDK metrics (`temporal_*`)

Exposed by the Temporal Java SDK via Micrometer on `/actuator/prometheus` and scraped
every 15s. The collector applies two metric relabel rules:

1. **Rename** `namespace` → `temporal_namespace` (avoids conflict with Sumo's reserved `namespace` dimension)
2. **Drop** the original `namespace` label

The `_sourceCategory` is set to `springboot-demo` via a relabel config in
`otel-collector-config.yaml`.

Verify locally:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep temporal_workflow_completed_total
```

Example Sumo Metrics query:

```
metric=temporal_workflow_completed_total _sourceCategory=springboot-demo temporal_namespace=your-namespace
```

Key metric families: `temporal_workflow_completed_total`, `temporal_request_total`,
`temporal_request_failure_total`, `temporal_request_latency_seconds_*`,
`temporal_workflow_endtoend_latency_seconds_*`, `temporal_activity_execution_latency_seconds_*`,
`temporal_activity_succeed_endtoend_latency_seconds_*`, `temporal_worker_task_slots_available`,
`temporal_worker_task_slots_used`, `temporal_num_pollers`, `temporal_sticky_cache_hit_total`,
`temporal_sticky_cache_size`, `temporal_workflow_active_thread_count`.

### 9.2 Temporal Cloud server metrics (`temporal_cloud_v1_*`)

Scraped from `https://metrics.temporal.io/v1/metrics` using `TEMPORAL_CLOUD_METRICS_API_KEY`.
Temporal only returns metrics that had activity in the **last completed 1-minute window** —
run workflows steadily and allow 3–5 minutes for data to appear in Sumo.

Example Sumo query:

```
metric=temporal_cloud_v1_workflow_success_count temporal_namespace=your-namespace
```

---

## 10. Sumo Logic Dashboards

Two Sumo Logic dashboard JSON files are included for import (**Library → Import**):

| File | Metrics | Variables |
|------|---------|-----------|
| `workermetrics-sumologic.json` | `temporal_*` worker/SDK | `temporal_namespace`, `task_queue`, `workflow_type` |
| `Cloud-metrics-sumologic.json` | `temporal_cloud_v1_*` server | `temporal_namespace` |

### 10.1 Worker dashboard (`workermetrics-sumologic.json`)

28 panels covering: workflow completions, request failures, active threads, task
poll success/empty, workflow & activity latencies (avg via `sum/count` division),
task slot utilization, poller/worker starts, sticky cache hits/size/evictions, and
RPC request breakdowns by operation.

- **Top row:** 3 single-value panels (Workflows Completed, Request Failures, Active Workflow Threads)
- **Grid:** 3-column layout (width 8 each, height 9) with time-series line charts
- **Queries** use `_sourceCategory=springboot-demo` and filter on `temporal_namespace`, `task_queue`, `workflow_type`
- **Latency panels** use a 3-query pattern: `#A` = `_sum`, `#B` = `_count`, `#C` = `#A / #B` for average
- **Variables** are CSV-defined with defaults matching this demo:
  - `temporal_namespace`: `test-100.ihmbh`, `default`
  - `task_queue`: `HELLO_TASK_QUEUE`
  - `workflow_type`: `HelloWorkflow`

### 10.2 Cloud dashboard (`Cloud-metrics-sumologic.json`)

Panels for Temporal Cloud server metrics: open/failed workflows, task backlog, sync
match rate, poll success, actions/operations, schedules, service requests/errors, and
latency percentiles. Queries use `_sourceCategory=temporal-cloud-metrics`.

### 10.3 Import steps

1. Open the Sumo Logic JSON file → copy all contents
2. Sumo Logic → **Library** → **Personal** → **⋮** → **Import**
3. Paste JSON, name the dashboard, click **Import**
4. Set variables to match your namespace / task queue / workflow type
5. Generate traffic (`curl` the hello endpoint), wait 2–5 minutes, refresh

> Updating the JSON file in this repo does **not** update an already-imported dashboard —
> re-import or edit panels in Sumo manually.

---

## 11. Project Structure

```
├── src/main/java/com/example/demo/
│   ├── DemoApplication.java       # Spring Boot entry point
│   ├── TemporalConfig.java        # Worker, client, Micrometer metrics bridge
│   ├── TemporalController.java    # GET /temporal/hello
│   ├── HelloWorkflow*.java        # Workflow definition + implementation
│   └── HelloActivities*.java      # Activity definition + implementation
├── src/main/resources/
│   └── application.properties     # Actuator / Prometheus exposure
├── otel-collector-config.yaml     # Collector: logs + Prometheus scrapes
├── docker-compose.yml             # App, Temporal dev server, collector
├── Dockerfile                     # Runs pre-built JAR from target/
├── .env.example                   # Template for secrets (copy to .env)
├── workermetrics-sumologic.json        # Sumo Logic import – worker dashboard (28 panels)
└── Cloud-metrics-sumologic.json        # Sumo Logic import – Cloud dashboard (43 panels)
```

---

## 12. Troubleshooting Guide

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Container exits on startup | JAR missing | Run `mvn clean package` first |
| Collector auth errors | Bad/missing installation token | Check `SUMOLOGIC_INSTALLATION_TOKEN` in `.env` |
| Worker can't reach Temporal Cloud | DNS or bad target/key | Verify `TEMPORAL_*` in `.env`; DNS 8.8.8.8 is set in compose |
| Cloud metrics scrape 401/403 | Wrong key type | Use a **Metrics Read-Only** key, not the worker namespace key |
| Dashboard shows `--` | Wrong query or no data | Re-import latest `*-sumologic.json`; verify metrics in Metrics Explorer; set variables correctly |
| Worker dashboard empty | `namespace=` filter | Use `temporal_namespace=` and `_sourceCategory=springboot-demo`; collector renames `namespace` → `temporal_namespace` via `metric_relabel_configs` |
| Worker dashboard variables blank | CSV defaults don't match | Edit variable CSV values in the imported dashboard or in `workermetrics-sumologic.json` before import |
| Cloud dashboard panels empty | No recent activity | Run workflows for several minutes; Cloud metrics are 1-minute windowed |

---

## 13. Local Development

Run the app outside Docker (local Temporal on host):

```bash
mvn spring-boot:run
# Worker connects to localhost:7233 by default (no .env Temporal vars)
```

Build only:

```bash
mvn clean package
```

---

## 14. Trade-offs and Considerations

These are summarized in [Section 3.2](#32-trade-offs-to-consider) above, and are
repeated here for quick reference:

- Sumo Logic is a paid, usage-based service.
- Dashboard JSON files must be re-imported to apply changes to an existing dashboard.
- Sumo Logic's metrics query syntax differs from PromQL and required manual translation
  of the original Grafana dashboards.

---

## 15. Conclusion

This project demonstrates a complete, working example of running a Temporal workflow
with a Spring Boot worker, while capturing both application logs and metrics — from
the worker itself and from Temporal Cloud — in a single, managed observability
platform. The setup instructions above are sufficient to run the demo end-to-end, and
the accompanying dashboards provide immediate visibility into workflow execution,
worker health, and Temporal Cloud server behavior.

For further detail on any individual component, refer to the corresponding
configuration file in the repository: `docker-compose.yml`, `otel-collector-config.yaml`,
`.env.example`, `workermetrics-sumologic.json`, and `Cloud-metrics-sumologic.json`.
