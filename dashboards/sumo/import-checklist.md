# Dashboard Import Checklist

## Files

| Dashboard | File |
|---|---|
| Worker SDK metrics | `dashboards/sumo/worker-sdk/worker-metrics-dashboard.json` |
| Temporal Cloud metrics | `dashboards/sumo/temporal-cloud/temporal-cloud-metrics-dashboard.json` |
| Temporal Self-Hosted Server | `dashboards/sumo/temporal-self-hosted/temporal-server-metrics-dashboard.json` |

## Import Steps

1. Import the JSON through Sumo Logic Library import.
2. Set the worker dashboard `Service` variable to the real service dimension. The default is `temporal-java-sumo-observability`.
3. Set `Namespace`, `Task Queue`, and `Workflow Type` variables to real low-cardinality values.
4. For the Cloud dashboard, set `Cloud Service` to `temporal-cloud` unless the collector service label was intentionally changed.
5. For the Self-Hosted dashboard, set the `Service Role` (`service_name`: `frontend`/`history`/`matching`/`worker`, or `*`) and `Temporal Namespace` (`namespace`, or `*`) variables. Do not expect a `service` or `cluster` dimension — scraped server metrics do not carry one.
6. Run one workflow through the app before judging empty panels.
7. Validate the exact labels in Sumo before locking the dashboard into a shared folder.

For API imports, post each dashboard JSON to:

```text
POST /api/v2/content/folders/<folder-id>/import?overwrite=true
```

The worker dashboard JSON intentionally avoids generated panel IDs, variable IDs, and `rootPanel` so the Content Import API can accept it consistently.

## Maintainability Rules

- Keep dashboard JSON in this repo so it is versioned with the app and manifests.
- Do not hard-code one Sumo account, namespace, or task queue into shared dashboard JSON.
- Keep Docker and EKS metric dimension differences behind dashboard variables.
- Prefer `service` and Temporal namespace variables over `_sourceCategory` for metrics dashboards.
- Add monitors separately once thresholds are agreed.
