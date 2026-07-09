# Sumo Logic Collection Path

The recommended EKS evaluation path is Sumo Logic Kubernetes Collection installed with Helm.

This repo keeps the Docker collector for local validation only. For EKS, install the supported Sumo chart so Kubernetes logs, Kubernetes metrics, pod metadata, and annotated application metrics are handled by the platform collector.

## Helm Install Shape

Do not run this until the AWS account, cluster, and Sumo account are approved.

```bash
export SUMO_ACCESS_ID="replace"
export SUMO_ACCESS_KEY="replace"
export SUMO_CLUSTER_NAME="replace-with-eks-cluster-name"

helm repo add sumologic https://sumologic.github.io/sumologic-kubernetes-collection
helm repo update

umask 077
printf 'SUMOLOGIC_ACCESSID=%s\nSUMOLOGIC_ACCESSKEY=%s\n' \
  "$SUMO_ACCESS_ID" "$SUMO_ACCESS_KEY" > /tmp/sumo-api-secret.env

kubectl create namespace sumologic
kubectl -n sumologic create secret generic sumo-api-secret \
  --from-env-file=/tmp/sumo-api-secret.env
rm -f /tmp/sumo-api-secret.env

helm upgrade --install sumologic sumologic/sumologic \
  --namespace sumologic \
  --create-namespace \
  -f sumo/kubernetes-collection-values.example.yaml \
  --set sumologic.clusterName="$SUMO_CLUSTER_NAME"
```

## What The Chart Collects

| Signal | EKS collection path |
|---|---|
| Application logs | Container stdout/stderr through the Sumo Kubernetes log pipeline. |
| Java SDK worker metrics | Pod annotation scrape of `/actuator/prometheus`. |
| Kubernetes metrics | Built-in cluster, node, pod, and workload collection. |
| Kubernetes events | Built-in event collection. |
| Temporal Cloud metrics | Dedicated collector in this directory scrapes Temporal Cloud OpenMetrics and exports to the Helm chart's OTLP metrics source. |

## Temporal Cloud Metrics

Include this path for Temporal Cloud service-side metrics. The Kubernetes Helm chart does not automatically scrape `metrics.temporal.io`.

Create the Temporal Cloud Metrics Read-Only secret:

```bash
kubectl -n sumologic create secret generic temporal-cloud-otel-collector-secrets \
  --from-literal=TEMPORAL_CLOUD_METRICS_API_KEY="$TEMPORAL_CLOUD_METRICS_API_KEY"
```

Deploy the scrape collector:

```bash
kubectl apply -f sumo/temporal-cloud-otel-collector.yaml
kubectl -n sumologic rollout status deployment/temporal-cloud-otel-collector
```

The collector scrapes on Temporal Cloud's one-minute aggregation cadence, exposes an OpenTelemetry health endpoint for Kubernetes probes, uses memory limiting and retry/queue controls, and includes a PodDisruptionBudget. Keep it at one active scraping replica unless the target design provides deduplication or scrape sharding; blindly running duplicate Prometheus receivers can duplicate metric ingestion.

Validated Sumo metric selector:

```text
service=temporal-cloud temporal_namespace=<temporal-cloud-namespace> metric=temporal_cloud_v1_*
```

The EKS path uses the Sumo Access ID and Access Key supplied to the Helm release. The dedicated Temporal Cloud collector exports to the OTLP metrics source created by that release.

## Credentials Needed Later

- Sumo Access ID.
- Sumo Access Key.
- Sumo deployment/region if your account is not the default endpoint.
- EKS cluster name to show in Sumo.
- Temporal Cloud Metrics Read-Only API key for the Cloud dashboard.

No Sumo or AWS credential belongs in this repository.

## API Validation Credentials

The same Sumo access key can install the Helm collector and list collectors, but API evidence collection needs query permissions too.

For automated validation, create or update a Sumo access key whose user/role can run:

- Log Search API queries: include the `runLogSearch` key scope.
- Metrics Query API queries: include the `runMetricsQuery` key scope.
- Collector lookup: retain collector read access so the validation can confirm the Kubernetes collector is alive.

Without those query scopes, validate from the Sumo UI using the queries in `sumo/validation-queries.md`.

## Monitor Specification

Use `sumo/production-monitor-specification.md` as the implementation contract for Sumo monitors. It corrects Java-specific metric assumptions, separates terminal workflow failures from SDK transport failures, defines throttling and limit signals, and records trigger, recovery, grouping, ownership, and validation requirements.
