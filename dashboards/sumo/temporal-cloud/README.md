# Temporal Cloud Dashboard

`temporal-cloud-metrics-dashboard.json` covers `temporal_cloud_v1_*` metrics from Temporal Cloud OpenMetrics.

This dashboard is part of the EKS reference path when the target Temporal environment is Temporal Cloud and a metrics read-only API key is available.

The Sumo Kubernetes Collection chart does not automatically scrape Temporal Cloud metrics. Keep a separate OpenMetrics scrape path for this dashboard.

For the EKS path in this repo, deploy `sumo/temporal-cloud-otel-collector.yaml`. The dashboard defaults to:

```text
service=temporal-cloud
```

Set `Temporal Namespace` to the target Temporal Cloud namespace before judging empty panels.
