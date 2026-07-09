# Sumo Validation Queries

Use these after the collector is installed and the app has processed at least one workflow.

If validating by API, the Sumo access key must be allowed to run log and metrics queries. A collector-only key can prove the collector exists but cannot prove data landed in Sumo search.

Use `docs/dashboard-scenario-drivers.md` to generate success, failure, timeout, cancel, terminate, backlog, schedule, and load signals before judging dashboard coverage.

## Worker SDK Metrics

```text
metric=temporal_workflow_completed_total service=temporal-java-sumo-observability temporal_namespace=<namespace>
```

```text
metric=temporal_worker_task_slots_available service=temporal-java-sumo-observability task_queue=<task-queue>
```

```text
metric=temporal_activity_execution_latency_seconds_count service=temporal-java-sumo-observability temporal_namespace=<namespace>
```

The worker dashboard intentionally calculates average latency from `_sum` and `_count` series. Temporal's published Prometheus/Grafana SDK dashboard uses histogram `_bucket` series for percentile panels, but the validated Sumo OTLP path for this repo did not ingest worker `_bucket` metrics.

## Application Logs

```text
_sourceCategory=kubernetes/temporal/java/worker "HelloWorkflow"
```

```text
_sourceCategory=kubernetes/temporal/java/worker "Temporal worker started"
```

```text
_sourceCategory=kubernetes/temporal/java/worker "SumoApiCheck"
```

## Kubernetes Metadata Checks

Field names can vary by Sumo account configuration, so validate the actual metadata before finalizing dashboard filters:

```text
_sourceCategory=kubernetes/temporal/java/worker | count by _sourceCategory, _sourceName
```

```text
metric=temporal_workflow_completed_total service=temporal-java-sumo-observability | count by namespace, pod, temporal_namespace
```

## Temporal Cloud Metrics

Only use these if a Temporal Cloud metrics scrape is configured:

```text
service=temporal-cloud temporal_namespace=<namespace> metric=temporal_cloud_v1_workflow_success_count
```

```text
service=temporal-cloud temporal_namespace=<namespace> metric=temporal_cloud_v1_approximate_backlog_count
```

```text
metric=temporal_cloud_v1_* service=temporal-cloud temporal_namespace=<namespace> | count by metric, service, temporal_namespace
```

In the EKS path, these metrics arrive through the Sumo Helm chart's OTLP HTTP source. Use `service=temporal-cloud` and `temporal_namespace` as the dashboard filters.

### Dashboard Population Notes

Temporal Cloud OpenMetrics exports the most recent completed one-minute aggregate. Expect short delays after a workflow run before data appears in Sumo.

Expected populated metrics after an EKS workflow burst:

```text
metric=temporal_cloud_v1_workflow_success_count service=temporal-cloud temporal_namespace=<namespace>
metric=temporal_cloud_v1_poll_success_count service=temporal-cloud temporal_namespace=<namespace>
metric=temporal_cloud_v1_operations_count service=temporal-cloud temporal_namespace=<namespace>
metric=temporal_cloud_v1_total_action_count service=temporal-cloud temporal_namespace=<namespace>
metric=temporal_cloud_v1_service_latency_p95 service=temporal-cloud temporal_namespace=<namespace> operation=StartWorkflowExecution
metric=temporal_cloud_v1_action_limit service=temporal-cloud temporal_namespace=<namespace>
metric=temporal_cloud_v1_service_request_limit service=temporal-cloud temporal_namespace=<namespace>
```

Expected populated metrics after a no-worker task queue scenario:

```text
metric=temporal_cloud_v1_approximate_backlog_count service=temporal-cloud temporal_namespace=<namespace> temporal_task_queue=<no-worker-task-queue>
metric=temporal_cloud_v1_poll_timeout_count service=temporal-cloud temporal_namespace=<namespace>
metric=temporal_cloud_v1_workflow_terminate_count service=temporal-cloud temporal_namespace=<namespace>
```

Expected populated metric after a short-lived Temporal Schedule:

```text
metric=temporal_cloud_v1_schedule_action_success_count service=temporal-cloud temporal_namespace=<namespace>
```

Blank panels are expected until the matching scenario occurs. Examples: schedule overrun, missed catchup, and rate-limited panels require those schedule conditions, cancellation panels require a workflow that is actually canceled, timeout panels require exported timeout counters, resource exhausted panels require throttling, and replication lag panels require a replicated namespace.
