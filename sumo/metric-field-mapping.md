# Metric And Field Mapping

## Worker Metrics

| Metric | Why it matters |
|---|---|
| `temporal_workflow_completed_total` | Workflow completion throughput. |
| `temporal_request_failure_total` | SDK request failures by operation/status. |
| `temporal_workflow_endtoend_latency_seconds_*` | End-to-end workflow latency. |
| `temporal_activity_execution_latency_seconds_*` | Activity execution latency. |
| `temporal_activity_schedule_to_start_latency_seconds_*` | Worker availability and scheduling delay. |
| `temporal_workflow_task_schedule_to_start_latency_seconds_*` | Workflow task pickup delay. |
| `temporal_worker_task_slots_available` | Remaining worker execution capacity. |
| `temporal_worker_task_slots_used` | Worker slot pressure. |
| `temporal_worker_start_total` | Worker process lifecycle and rollout diagnostics. |
| `temporal_poller_start_total` | Poller-start lifecycle events. This is not a current-poller gauge. |
| `temporal_workflow_task_execution_failed_total` | Workflow task failures such as workflow code, replay, or serialization problems. |
| `temporal_activity_execution_failed_total` | Activity execution failures observed by the worker. |
| `temporal_local_activity_execution_failed_total` | Local Activity failures, when Local Activities are used. |
| `temporal_resource_slots_cpu_usage` / `temporal_resource_slots_mem_usage` | Java worker resource-based slot pressure where resource-based tuning is enabled. |
| `temporal_sticky_cache_hit_total` | Sticky cache effectiveness. |
| `temporal_sticky_cache_total_forced_eviction_total` | Sticky cache pressure. |

The Java SDK does not emit `temporal_num_pollers`. Use Kubernetes readiness and replica health as the worker-presence signal, then corroborate with `temporal_cloud_v1_no_poller_tasks_count` and backlog. Do not infer current availability from the cumulative `temporal_poller_start_total` counter.

## Recommended Dashboard Fields

| Field | Source |
|---|---|
| `temporal_namespace` | Added by the app/SDK metric labels or collector relabeling. |
| `temporal_task_queue` | Added by Spring management common tags. |
| `task_queue` | Temporal SDK metric label used by current dashboard queries. |
| `workflow_type` | Temporal SDK metric label. |
| `activity_type` | Temporal SDK metric label. |
| `worker_type` | Temporal SDK metric label. |
| `namespace`, `pod`, `container` | Kubernetes metadata enriched by Sumo Kubernetes Collection. |
| `service` | Sumo metric dimension; worker dashboard variable defaults to `temporal-java-sumo-observability`. |
| `_sourceCategory` | Sumo metric source category. This can vary by account; do not use it as the primary worker dashboard filter. |

## Temporal Cloud Metrics

| Metric | Why it matters |
|---|---|
| `temporal_cloud_v1_approximate_backlog_count` | Service-side task queue backlog. |
| `temporal_cloud_v1_no_poller_tasks_count` | Task queues with no active pollers. |
| `temporal_cloud_v1_service_request_count` | Temporal Cloud frontend request volume. |
| `temporal_cloud_v1_service_error_count` | Temporal Cloud service errors by operation. |
| `temporal_cloud_v1_service_request_throttled_count` | Frontend requests throttled by service limits. |
| `temporal_cloud_v1_operations_throttled_count` | Namespace operations throttled by rate limits. |
| `temporal_cloud_v1_total_action_throttled_count` | Namespace actions throttled by the action limit. |
| `temporal_cloud_v1_resource_exhausted_error_count` | Resource exhaustion errors. This metric explicitly excludes namespace-limit throttling. |

For the EKS path, the Cloud scrape collector adds:

| Field | Value |
|---|---|
| `service` | `temporal-cloud` |
| `temporal_namespace` | Temporal Cloud namespace requested from `metrics.temporal.io`. |

## Temporal Self-Hosted Server Metrics

These are native Temporal **server** metrics, emitted by the Frontend, History,
Matching, and internal Worker roles at the server `/metrics` endpoint. They are
**not** prefixed with `temporal_server_` (that prefix does not exist). Every name
below is verified against Temporal's metric registry
(`common/metrics/metric_defs.go`) and the official `temporalio/dashboards` server
dashboards.

| Metric | Why it matters |
|---|---|
| `service_requests` | Server RPC request volume by `service_name`/`operation`. Request denominator. |
| `service_errors` / `service_error_with_type` | Server errors (untyped / typed via `error_type`). Build ratios against `service_requests`. |
| `service_latency` (`_bucket`/`_sum`/`_count`) | Server RPC latency histogram. Average = `rate(_sum)/rate(_count)`. |
| `service_pending_requests` | In-flight/pending request pressure (gauge). |
| `client_requests` / `client_errors` / `client_latency` | Internal inter-service client calls, keyed by role/operation. |
| `approximate_backlog_count` | Matching task-queue backlog depth. Direct backlog signal. |
| `approximate_backlog_age_seconds` | Age of the oldest queued task. Queue-delay SLOs. |
| `no_poller_tasks` | Tasks arriving with no recent poller. Corroborate with worker/K8s health. |
| `poll_success` / `poll_success_sync` / `poll_timeouts` | Matching poll and sync-match behavior. |
| `persistence_requests` | Persistence request volume by `operation`. |
| `persistence_errors` / `persistence_error_with_type` | Persistence errors (untyped / typed). |
| `persistence_errors_resource_exhausted` | Capacity/resource-exhaustion persistence errors, separated from general errors. |
| `persistence_latency` (`_bucket`/`_sum`/`_count`) | Persistence latency histogram. |
| `cache_size` / `cache_usage` / `cache_pinned_usage` | History cache families, grouped by `cache_type`. |
| `restarts` | Server process restart counter (by `service_name`). |
| `num_goroutines` / `memory_heap` / `memory_heapinuse` | Go runtime pressure; compare against pod CPU/memory limits. |
| `action` | Server action counter. Self-hosted has no SaaS action-limit/throttle telemetry. |
| `schedule_to_start_timeout` / `start_to_close_timeout` | Task pickup / execution timeout counters (by `operation`). |

Recommended dimensions: `service_name` (`frontend`/`history`/`matching`/`worker`/`server`),
`operation`, `namespace`, `cache_type`. The native role tag is `service_name` — it also
tags server runtime metrics (`restarts`, `num_goroutines`, `memory_heap`), verified on
Temporal Server 1.31.2. `service_role` also exists but Temporal's own dashboards group by
`service_name`; do **not** rely on an invented `temporal_role` tag.

Collection and naming caveats for the EKS path:

| Concern | Note |
|---|---|
| No dedicated collector | Server metrics are scraped in-cluster by the Sumo Kubernetes Collection via `prometheus.io/*` pod annotations; there is no Cloud-style collector and no synthetic `service=temporal-self-hosted` dimension. |
| Counter suffixes | Tally/Prometheus export uses bare names (`service_requests`); the OpenTelemetry framework adds `_total`. Inventory the landed names first. |
| Namespace label collision | Temporal's own `namespace` label can collide with the Kubernetes namespace added by collection. Confirm whether the Temporal namespace lands as `namespace` or `exported_namespace` before filtering. |

## Callout

Do not collapse worker metrics, Temporal Cloud metrics, and Temporal Self-Hosted metrics into one dashboard. They answer different questions:

- Worker metrics show application-side poller, slot, workflow, activity, and SDK request health.
- Temporal Cloud and Self-Hosted metrics show service-side namespace/task-queue behavior exposed by the server.
