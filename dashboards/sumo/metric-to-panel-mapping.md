# Metric To Panel Mapping

| Dashboard area | Primary metrics |
|---|---|
| Workflow throughput | `temporal_workflow_completed_total` |
| Workflow failures | `temporal_request_failure_total`, `temporal_cloud_v1_workflow_failed_count`, `temporal_workflow_task_execution_failed_total` |
| Activity failures | `temporal_activity_execution_failed_total`, `temporal_cloud_v1_activity_fail_count`, `temporal_cloud_v1_activity_task_fail_count`, `temporal_cloud_v1_activity_timeout_count`, `temporal_cloud_v1_activity_task_timeout_count` |
| Workflow latency | `temporal_workflow_endtoend_latency_seconds_sum`, `temporal_workflow_endtoend_latency_seconds_count` |
| Workflow task pickup | `temporal_workflow_task_schedule_to_start_latency_seconds_sum`, `temporal_workflow_task_schedule_to_start_latency_seconds_count` |
| Activity execution | `temporal_activity_execution_latency_seconds_sum`, `temporal_activity_execution_latency_seconds_count` |
| Activity pickup | `temporal_activity_schedule_to_start_latency_seconds_sum`, `temporal_activity_schedule_to_start_latency_seconds_count` |
| Worker saturation | `temporal_worker_task_slots_used`, `temporal_worker_task_slots_available` |
| Worker lifecycle | `temporal_worker_start_total`, `temporal_poller_start_total`; use Kubernetes readiness/replica metrics for current availability |
| Sticky cache | `temporal_sticky_cache_hit_total`, `temporal_sticky_cache_size`, `temporal_sticky_cache_total_forced_eviction_total` |
| Temporal Cloud backlog | `temporal_cloud_v1_approximate_backlog_count`, `temporal_cloud_v1_no_poller_tasks_count` |
| Temporal Cloud service health | `temporal_cloud_v1_service_request_count`, `temporal_cloud_v1_service_error_count`, `temporal_cloud_v1_service_latency_p50`, `temporal_cloud_v1_service_latency_p95`, `temporal_cloud_v1_service_latency_p99` |
| Temporal Cloud throttling | `temporal_cloud_v1_service_request_throttled_count`, `temporal_cloud_v1_operations_throttled_count`, `temporal_cloud_v1_total_action_throttled_count`, `temporal_cloud_v1_resource_exhausted_error_count` |
| Temporal Cloud limits | `temporal_cloud_v1_action_limit`, `temporal_cloud_v1_service_request_limit` |
| Temporal Cloud service latency | `temporal_cloud_v1_service_latency_p50`, `temporal_cloud_v1_service_latency_p95`, `temporal_cloud_v1_service_latency_p99` |
| Temporal Cloud schedules | `temporal_cloud_v1_schedule_action_success_count`, `temporal_cloud_v1_schedule_buffer_overruns_count`, `temporal_cloud_v1_schedule_missed_catchup_window_count`, `temporal_cloud_v1_schedule_rate_limited_count` |
| Temporal Cloud replication | `temporal_cloud_v1_replication_lag_p50`, `temporal_cloud_v1_replication_lag_p95`, `temporal_cloud_v1_replication_lag_p99` (without aggregation) |
| Temporal Cloud actions | `temporal_cloud_v1_total_action_count` (shown as average actions/sec over the panel window; not a cumulative action total) |

## Temporal Self-Hosted Server Dashboard

Every metric family below is verified against Temporal's metric registry
(`common/metrics/metric_defs.go`) and the official `temporalio/dashboards`
server dashboards. Server roles are split by the native `service_name` tag
(`frontend`, `history`, `matching`, `worker`) — the same tag Temporal's own
server dashboards use. Do not filter self-hosted panels by a `service`
dimension; scraped server metrics do not carry one.

| Dashboard area | Primary metrics |
|---|---|
| Service health (by role) | `service_requests`, `service_errors`, `service_pending_requests` |
| Service error ratio | `service_errors` / `service_requests` (formula) |
| Service latency (avg) | `service_latency_sum` / `service_latency_count` (formula) |
| Inter-service client health | `client_errors`, `client_requests`, `client_latency_bucket` |
| Workflow & task traffic | `service_requests` by `operation`, `action`, `workflow_success`, `workflow_failed` |
| Task timeouts | `schedule_to_start_timeout`, `start_to_close_timeout` (by `operation`) |
| Server topology | `service_requests` split by `service_name` |
| Matching / task queue | `approximate_backlog_count`, `approximate_backlog_age_seconds`, `no_poller_tasks`, `poll_success`, `poll_success_sync`, `poll_timeouts` |
| Persistence | `persistence_requests`, `persistence_errors`, `persistence_errors_resource_exhausted`, `persistence_latency_sum`/`_count` |
| History cache | `cache_size`, `cache_usage`, `cache_pinned_usage` by `cache_type` |
| Server runtime | `restarts`, `num_goroutines`, `memory_heap` by `service_name` |
| Kubernetes correlation | `kube_pod_container_status_restarts_total`, `container_cpu_usage_seconds_total`, `container_memory_working_set_bytes` |

Latency uses the average form `rate(*_latency_sum)/rate(*_latency_count)`:
Sumo has no `histogram_quantile`, so a true p95/p99 from `*_latency_bucket`
must be validated separately. Counter suffixes are framework-dependent — the
default Tally/Prometheus export uses bare names (`service_requests`), while the
OpenTelemetry framework adds `_total`. Inventory the exact landed names in the
Sumo account before locking filters.

The Temporal Cloud dashboard metric names were compared against Temporal's published OpenMetrics Grafana dashboard. Remaining blank panels should be treated as scenario coverage gaps unless the validation queries above also return no matching metric family after the scenario has run.

The Java SDK does not emit `temporal_num_pollers`. Any older dashboard panel that queried it has been removed. Current worker availability must be taken from Kubernetes readiness/replica health and corroborated with Temporal Cloud no-poller and backlog signals.
