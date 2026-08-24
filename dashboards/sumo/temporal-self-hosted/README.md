# Temporal Self-Hosted Server Metrics Dashboard

This directory contains the Sumo Logic dashboard JSON for a self-hosted Temporal
Server (`temporal-server-metrics-dashboard.json`).

## Intended Usage

This dashboard gives an operational view of the **Temporal Server** roles
(Frontend, History, Matching, and internal Worker), their persistence tier, and
server runtime. It is **not** a dashboard for your application workers — for
application workers use the `worker-sdk` dashboard. Keep the two separate so
server-role/persistence telemetry is not conflated with worker-side SDK
telemetry.

Sections:
- **Service Health & Availability** — `service_requests`, `service_errors`, error
  ratio, average `service_latency`, `service_pending_requests`, `client_errors`.
- **Workflow & Task Traffic** — frontend `service_requests` by `operation`,
  `action`, `workflow_success`/`workflow_failed`, `schedule_to_start_timeout` and
  `start_to_close_timeout`.
- **Server Topology** — `service_requests` split per role via `service_name`.
- **Matching / Task Queue Health** — `approximate_backlog_count`,
  `approximate_backlog_age_seconds`, `no_poller_tasks`, `poll_success`,
  `poll_success_sync`, `poll_timeouts`.
- **Persistence** — `persistence_requests`, `persistence_errors`, error ratio,
  average `persistence_latency`, `persistence_errors_resource_exhausted`.
- **History Cache & Server Runtime** — `cache_usage`/`cache_size` by `cache_type`,
  `restarts`/`num_goroutines`/`memory_heap` by `temporal_service_type`.
- **Infrastructure Correlation** — Kubernetes pod restarts, CPU, and memory for
  Temporal pods (Sumo Kubernetes Collection).

Every metric family is verified against Temporal's metric registry
(`common/metrics/metric_defs.go`) and the official `temporalio/dashboards` server
dashboards.

## Dependencies

The dashboard expects native Temporal Server metrics scraped in-cluster by the
Sumo Kubernetes Collection from the server `/metrics` endpoint via
`prometheus.io/*` pod annotations. A dedicated Cloud-style metrics collector is
**not** required. See `sumo/temporal-self-hosted-collection-values.example.yaml`.

Primary dimensions:
- `service_name` — the native role tag (`frontend`, `history`, `matching`,
  `worker`). This is what Temporal's own dashboards use; `service_role` also
  exists but is not used here.
- `operation` — RPC or persistence operation.
- `namespace` — Temporal namespace.
- `cache_type` — history cache family.
- `temporal_service_type` — role tag on server runtime metrics (`restarts`, etc.).

## Variables

- `service_name` (Service Role) — default `*`; also `frontend`/`history`/`matching`/`worker`.
- `namespace` (Temporal Namespace) — default `*`.

There is intentionally **no** `service` variable: scraped server metrics do not
carry a `service` dimension, so filtering by one would blank every panel.

## Inventory-first caveat

Exposed names are framework-dependent. The default Tally/Prometheus export uses
bare names (`service_requests`); the OpenTelemetry framework adds `_total`. The
Kubernetes namespace added by collection can also shadow Temporal's own
`namespace` label as `exported_namespace`. Inventory the landed names before
locking filters:

```text
metric=service_requests | count by service_name, operation
```

Latency panels use the average form `rate(*_latency_sum)/rate(*_latency_count)`
because Sumo has no `histogram_quantile`; a true p95/p99 from `*_latency_bucket`
must be validated separately.

## Validation

After importing, confirm metrics arrive from more than one `service_name`:

```text
metric=service_requests service_name=frontend
metric=service_requests service_name=history
```

Full validation query set: `sumo/validation-queries.md`.
