# Dashboard Scenario Drivers

The sample app includes optional HTTP endpoints for deliberately populating Sumo dashboard panels. These are validation helpers, not production APIs.

Start the app, then run the scenarios you need:

```bash
curl -X POST "http://localhost:8080/temporal/scenarios/success?count=10"
curl -X POST "http://localhost:8080/temporal/scenarios/workflow-fail"
curl -X POST "http://localhost:8080/temporal/scenarios/activity-fail"
curl -X POST "http://localhost:8080/temporal/scenarios/timeout"
curl -X POST "http://localhost:8080/temporal/scenarios/cancel"
curl -X POST "http://localhost:8080/temporal/scenarios/terminate"
curl -X POST "http://localhost:8080/temporal/scenarios/continue-as-new"
curl -X POST "http://localhost:8080/temporal/scenarios/task-fail"
curl -X POST "http://localhost:8080/temporal/scenarios/burst?count=50"
```

To create Cloud backlog/no-worker metrics:

```bash
curl -X POST "http://localhost:8080/temporal/scenarios/backlog?count=3&noWorkerTaskQueue=DASHBOARD_NO_WORKER_TQ"
```

To create schedule success metrics:

```bash
curl -X POST "http://localhost:8080/temporal/scenarios/schedule?remainingActions=2&intervalSeconds=30"
```

If validation ends early, delete the schedule returned by the create call:

```bash
curl -X DELETE "http://localhost:8080/temporal/scenarios/schedule/<schedule-id>"
```

## Scenario Coverage

| Scenario | Primary dashboard impact |
|---|---|
| `success` | Workflow throughput, activity execution, poll success, service request latency. |
| `workflow-fail` | Workflow failure counters. |
| `activity-fail` | Activity failure and SDK request failure signals. |
| `timeout` | Workflow timeout counters after the configured execution timeout. |
| `cancel` | Workflow cancellation counters. |
| `terminate` | Workflow termination counters. |
| `continue-as-new` | Continued-as-new counters. |
| `task-fail` | Workflow-task failure and retry behavior; the run is bounded by execution timeout. |
| `burst` | Request volume and latency panels. |
| `backlog` | Cloud task backlog and no-worker style validation. |
| `schedule` | Temporal Cloud schedule action success metrics. |

## Guardrails

- `count` is capped at 200 per call.
- `task-fail`, `timeout`, and `backlog` use bounded workflow execution timeouts.
- These endpoints should be disabled, removed, or access-controlled before adapting the sample for a production app.
- Temporal Cloud OpenMetrics can lag by a few minutes because it exports completed one-minute aggregates.

