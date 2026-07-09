# Worker SDK Dashboard

`worker-metrics-dashboard.json` covers Temporal Java SDK metrics emitted by the Spring Boot worker through:

```text
Temporal Java SDK -> Tally -> Micrometer -> /actuator/prometheus -> Sumo
```

The dashboard is intended for both:

- Docker local validation through `docker/otel-collector-config.yaml`.
- EKS validation through Sumo Kubernetes Collection pod annotation scraping.

The `Service` dashboard variable defaults to `temporal-java-sumo-observability`. Change it after import if the Sumo account uses a different service dimension.

Compared with Temporal's Prometheus/Grafana SDK dashboard, this Sumo version keeps latency panels on `_sum` and `_count` average-latency queries. The validated Sumo OTLP path for this repo did not ingest worker histogram `_bucket` series, so Prometheus `histogram_quantile` panels are not directly portable without changing the metrics pipeline.
