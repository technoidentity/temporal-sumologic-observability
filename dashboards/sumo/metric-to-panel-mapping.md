# Metric To Panel Mapping

| Dashboard area | Primary metrics |
|---|---|
| Workflow throughput | `temporal_workflow_completed_total` |
| Workflow failures | `temporal_request_failure_total`, `temporal_cloud_v1_workflow_failed_count` |
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
| Temporal Cloud replication | `temporal_cloud_v1_replication_lag_p50`, `temporal_cloud_v1_replication_lag_p95`, `temporal_cloud_v1_replication_lag_p99` |

The Temporal Cloud dashboard metric names were compared against Temporal's published OpenMetrics Grafana dashboard. Remaining blank panels should be treated as scenario coverage gaps unless the validation queries above also return no matching metric family after the scenario has run.

The Java SDK does not emit `temporal_num_pollers`. Any older dashboard panel that queried it has been removed. Current worker availability must be taken from Kubernetes readiness/replica health and corroborated with Temporal Cloud no-poller and backlog signals.
