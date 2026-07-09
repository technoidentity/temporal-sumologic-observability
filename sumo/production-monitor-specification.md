# Temporal on Sumo Logic - Production Monitor Specification

## Status And Use

This is the implementation contract for environment-specific Sumo Logic monitors. It defines the signal, query shape, starting threshold, recovery behavior, grouping, and response owner. It does not claim that monitors have already been created in a target Sumo Logic account.

Before enabling paging:

1. Validate every metric name and dimension in the target account.
2. Baseline at least one representative normal operating cycle.
3. Replace each starting threshold with a workload SLO or an approved capacity threshold.
4. Set warning and critical notification routes, maintenance windows, and runbook URLs.
5. Exercise the monitor with the matching scenario and record evidence.

## Required Monitor Fields

Every Sumo monitor created from this catalog must record:

| Field | Required value |
|---|---|
| Name | Environment, namespace, task queue or workflow scope, and condition. |
| Detection method | Static, anomaly, missing data, or SLO. |
| Query | Version-controlled query with validated dimensions. |
| Trigger | Warning and critical threshold, occurrence behavior, and evaluation window. |
| Recovery | Recovery threshold and resolution window. |
| Grouping | Alert per service, namespace, task queue, workflow type, activity type, or operation as appropriate. |
| Routing | Warning route, critical route, escalation, and maintenance window behavior. |
| Ownership | Application, EKS/platform, Temporal platform, Sumo platform, or shared. |
| Runbook | First checks, likely causes, rollback/escalation path, and evidence links. |
| Validation | Scenario, expected alert, actual result, date, and approver. |

## P0 Monitor Catalog

### M01 - Worker Deployment Unavailable

- Purpose: detect that the Java Worker deployment is not ready to poll.
- Primary source: Kubernetes deployment and pod metrics from Sumo Kubernetes Collection.
- Query shape A: `metric=kube_deployment_spec_replicas namespace=temporal-observability deployment=temporal-java-sumo-observability | max`
- Query shape B: `metric=kube_deployment_status_replicas_available namespace=temporal-observability deployment=temporal-java-sumo-observability | max`
- Condition: `#B < #A` at all times for 5 minutes; critical when `#B == 0` at all times for 5 minutes.
- Recovery: available replicas equal desired replicas for 5 minutes.
- Grouping: cluster, namespace, deployment.
- Owner: EKS/platform first; application owner if pods are ready but worker startup fails.
- Validation: scale deployment to zero or break the readiness probe in a non-production environment.
- Important: the Java SDK does not emit `temporal_num_pollers`. Do not use that metric.

Validate the exact Kubernetes metric names in the target Sumo account before creating this monitor; Sumo metadata conventions can vary by collection configuration.

### M02 - Tasks Added With No Poller

- Purpose: detect tasks accepted by Temporal Cloud when no Worker is polling the task queue.
- Query A: `metric=temporal_cloud_v1_no_poller_tasks_count service=temporal-cloud temporal_namespace=<namespace> | sum by temporal_task_queue,task_type`
- Query B: `metric=temporal_cloud_v1_approximate_backlog_count service=temporal-cloud temporal_namespace=<namespace> | max by temporal_task_queue,task_type`
- Warning: `#A > 0` at any time within 5 minutes.
- Critical: `#A > 0` and `#B > 0` at all times within 5 minutes for a production queue.
- Recovery: no-poller rate returns to zero and backlog is no longer increasing for 5 minutes.
- Grouping: namespace, task queue, task type.
- Owner: application/worker owner, with EKS/platform escalation.
- Validation: submit a workflow to a dedicated task queue with no Worker.

### M03 - Task Queue Saturation

- Purpose: distinguish active-but-saturated Workers from a missing Worker deployment.
- Query A: `metric=temporal_cloud_v1_approximate_backlog_count service=temporal-cloud temporal_namespace=<namespace> | max by temporal_task_queue,task_type`
- Query B: `metric=temporal_worker_task_slots_available service=<worker-service> temporal_namespace=<namespace> | min by task_queue,worker_type`
- Query C: worker workflow-task or activity schedule-to-start latency using the validated latency series.
- Warning: backlog exceeds the queue baseline for 10 minutes or available slots remain below 20 percent of total capacity.
- Critical: backlog is increasing while available slots are zero for 5 minutes, or schedule-to-start latency breaches the queue SLO.
- Recovery: backlog falls below the warning threshold and slots remain available for 10 minutes.
- Grouping: namespace, task queue, task type or worker type.
- Owner: application/worker owner.
- Validation: use the burst/backlog scenario with constrained worker concurrency.

### M04 - Workflow Terminal Failure Ratio

- Purpose: detect user-visible Workflow Execution failures and timeouts.
- Query A: `metric=temporal_cloud_v1_workflow_failed_count service=temporal-cloud temporal_namespace=<namespace> | sum by temporal_workflow_type,temporal_task_queue`
- Query B: `metric=temporal_cloud_v1_workflow_timeout_count service=temporal-cloud temporal_namespace=<namespace> | sum by temporal_workflow_type,temporal_task_queue`
- Query C: `metric=temporal_cloud_v1_workflow_success_count service=temporal-cloud temporal_namespace=<namespace> | sum by temporal_workflow_type,temporal_task_queue`
- Formula: `(#A + #B) / (#A + #B + #C)` with a minimum terminal-volume guard.
- Starting point: warning above 1 percent for 10 minutes; critical above 5 percent for 5 minutes. Replace with the approved workflow SLO.
- Recovery: ratio below the warning threshold for 15 minutes.
- Grouping: namespace, workflow type, task queue.
- Owner: application owner.
- Validation: workflow-fail and timeout scenarios.

### M05 - Workflow Task Execution Failure

- Purpose: detect workflow code, replay, serialization, and nondeterminism-style failures before they become broad terminal failures.
- Query: `metric=temporal_workflow_task_execution_failed_total service=<worker-service> temporal_namespace=<namespace> | sum by workflow_type,task_queue`
- Warning: any nonzero increase in 5 minutes in non-production.
- Critical: any sustained increase for a production workflow type, or repeated failures for the same workflow type after deployment.
- Recovery: no increase for 15 minutes after rollback or remediation.
- Grouping: service, namespace, workflow type, task queue.
- Owner: application owner.
- Validation: bounded task-fail scenario.

### M06 - Activity Terminal Failure And Retry Amplification

- Purpose: separate terminal business impact from retry-attempt pressure.
- Query A: `metric=temporal_cloud_v1_activity_fail_count service=temporal-cloud temporal_namespace=<namespace> | sum by temporal_workflow_type,temporal_activity_type,temporal_task_queue`
- Query B: `metric=temporal_cloud_v1_activity_timeout_count service=temporal-cloud temporal_namespace=<namespace> | sum by timeout_type,temporal_workflow_type,temporal_activity_type,temporal_task_queue`
- Query C: `metric=temporal_cloud_v1_activity_task_fail_count service=temporal-cloud temporal_namespace=<namespace> | sum by temporal_workflow_type,temporal_activity_type,temporal_task_queue`
- Query D: `metric=temporal_cloud_v1_activity_task_timeout_count service=temporal-cloud temporal_namespace=<namespace> | sum by timeout_type,temporal_workflow_type,temporal_activity_type,temporal_task_queue`
- Terminal condition: `#A + #B` above the activity SLO.
- Retry-amplification condition: attempts `#C + #D` rise materially faster than terminal failures while dependency latency or request volume is stable.
- Starting point: warn on a 2x baseline increase for 10 minutes; critical when terminal failures breach the approved SLO.
- Recovery: both terminal and attempt rates return below warning for 15 minutes.
- Grouping: namespace, workflow type, activity type, task queue, timeout type.
- Owner: application/dependency owner.
- Validation: activity-fail and timeout scenarios.
- Cardinality: `temporal_activity_type` is opt-in on the Cloud endpoint; enable it only after cost review.

### M07 - Worker To Temporal RPC Error Ratio

- Purpose: detect authentication, connectivity, throttling, and Temporal endpoint problems seen by the Java client.
- Query A: `metric=temporal_request_failure_total service=<worker-service> temporal_namespace=<namespace> | sum by operation,status_code`
- Query B: `metric=temporal_request_total service=<worker-service> temporal_namespace=<namespace> | sum by operation`
- Formula: `#A / #B` with a minimum request-volume guard.
- Starting point: warning above 1 percent for 10 minutes; critical above 5 percent for 5 minutes for Start, Signal, Respond, or Poll operations.
- Recovery: below warning for 15 minutes.
- Grouping: service, namespace, operation, status code.
- Owner: application owner, then network/Temporal platform depending on status.
- Validation: invalid credential in an isolated environment and bounded service interruption.

### M08 - Temporal Cloud Service Error Ratio And Latency

- Purpose: detect service-side errors and operation-specific latency.
- Error queries: `temporal_cloud_v1_service_error_count` divided by `temporal_cloud_v1_service_request_count`, grouped by operation.
- Latency query: `metric=temporal_cloud_v1_service_latency_p95 service=temporal-cloud temporal_namespace=<namespace> operation=<operation>`.
- Starting point: warning above 1 percent errors or p95 above the operation SLO for 10 minutes; critical above 5 percent errors or 2x the SLO for 5 minutes.
- Recovery: both error ratio and latency below warning for 15 minutes.
- Grouping: namespace, operation.
- Owner: Temporal platform owner; escalate to Temporal support when corroborated across workers.
- Important: do not aggregate precomputed percentile values across incompatible dimensions.

### M09 - Throttling And Namespace Limit Pressure

- Purpose: detect actual throttling and leading capacity pressure.
- Throttling metrics: `temporal_cloud_v1_service_request_throttled_count`, `temporal_cloud_v1_operations_throttled_count`, and `temporal_cloud_v1_total_action_throttled_count`.
- Usage/limit pairs: service requests versus `temporal_cloud_v1_service_request_limit`; operations versus `temporal_cloud_v1_operations_limit`; actions with `is_background=false` versus `temporal_cloud_v1_action_limit`; pending poll requests versus `temporal_cloud_v1_poller_limit`.
- Warning: sustained usage above 70 percent of limit for 15 minutes or any unexpected throttling.
- Critical: sustained usage above 90 percent, or throttling that affects production workflow operations for 5 minutes.
- Recovery: below 70 percent and no throttling for 15 minutes.
- Grouping: namespace and operation where available.
- Owner: Temporal platform/capacity owner.
- Important: `temporal_cloud_v1_resource_exhausted_error_count` is a separate resource-exhaustion signal and excludes namespace-limit throttling.

### M10 - Schedule Health

- Purpose: detect missed, delayed, or overrun scheduled executions.
- Queries: `temporal_cloud_v1_schedule_missed_catchup_window_count`, `temporal_cloud_v1_schedule_buffer_overruns_count`, and `temporal_cloud_v1_schedule_rate_limited_count`.
- Warning: any unexpected nonzero value.
- Critical: any value for a business-critical schedule, or continued increase for 5 minutes.
- Recovery: no increase for the schedule-specific recovery window.
- Grouping: namespace. Use a custom low-cardinality schedule group if per-schedule ownership is required.
- Owner: application owner.
- Validation: schedule scenarios for success, missed catch-up, overlap pressure, and rate limiting.

For low-volume schedules, add a business heartbeat or expected-success monitor. Generic rate alerts can miss one absent daily or monthly execution.

### M11 - Telemetry Data Freshness

- Purpose: detect a broken worker or Cloud collection path independently of application health.
- Worker heartbeat query: `metric=temporal_worker_task_slots_available service=<worker-service> temporal_namespace=<namespace>`.
- Cloud heartbeat query: `metric=temporal_cloud_v1_action_limit service=temporal-cloud temporal_namespace=<namespace>`.
- Detection method: Sumo Missing Data.
- Starting point: warning after 5 minutes and critical after 10 minutes, adjusted for approved maintenance windows.
- Recovery: stable data for 5 minutes.
- Grouping: service and namespace.
- Owner: Sumo/telemetry platform owner; route worker-only gaps to application/EKS and Cloud-only gaps to the Cloud collector owner.
- Validation: stop each collector path independently.

Do not use `metric=temporal_*` wildcard absence as the only heartbeat because many event metrics legitimately disappear during idle periods.

### M12 - Cloud Collector Health

- Purpose: detect failure of the dedicated Temporal Cloud OpenMetrics collector.
- Sources: Kubernetes unavailable replicas, pod restarts, OOMKilled events, collector exporter error logs, and M11 Cloud missing data.
- Warning: collector restart or exporter errors in 10 minutes.
- Critical: zero available replicas or Cloud heartbeat missing for 10 minutes.
- Recovery: deployment ready and Cloud heartbeat stable for 5 minutes.
- Grouping: cluster, namespace, deployment, container.
- Owner: EKS/Sumo telemetry owner.
- Validation: stop the collector deployment and test an invalid API key in a non-production environment.

### M13 - Worker Latency SLO

- Purpose: detect tail latency for workflow pickup, activity pickup, workflow completion, activity execution, and SDK RPCs.
- Required input: validated histogram `_bucket` series or a supported percentile series.
- Warning/critical: use the approved p95/p99 SLO per task queue, workflow type, activity type, or operation.
- Recovery: percentile below warning for 15 minutes.
- Owner: application owner.
- Current limitation: the validated Sumo path exposed `_sum` and `_count` but not worker `_bucket` series. Average latency is dashboard-only diagnostic coverage and is not an acceptable substitute for a tail-latency SLO.

## P1 Conditional Monitors

Create these only when the corresponding feature is used:

- Local Activity failure and latency: `temporal_local_activity_execution_failed_total` and local-activity latency metrics.
- Sticky cache thrashing: cache miss ratio and forced evictions, correlated with JVM memory.
- Resource-based slot pressure: `temporal_resource_slots_cpu_usage` and `temporal_resource_slots_mem_usage`.
- Multi-region replication: Cloud replication lag p95/p99 against the approved RPO.
- Nexus: Nexus task execution failure, latency, schedule-to-start latency, and no-task polling metrics.
- Business milestones: custom low-cardinality counters, latency histograms, and expected-event heartbeats.

## Validation Evidence Template

| Field | Value |
|---|---|
| Monitor ID and version |  |
| Environment |  |
| Metric dimensions validated |  |
| Scenario executed |  |
| Expected warning/critical |  |
| Actual warning/critical |  |
| Recovery verified |  |
| Notification route verified |  |
| Runbook verified |  |
| Execution date |  |
| Reviewer/approver |  |

## References

- Temporal SDK metrics: https://docs.temporal.io/references/sdk-metrics
- Temporal Cloud OpenMetrics metrics: https://docs.temporal.io/cloud/metrics/openmetrics/metrics-reference
- Sumo Logic monitor creation: https://www.sumologic.com/help/docs/alerts/monitors/create-monitor/
