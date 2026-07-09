# Kubernetes Deployment Path

This folder contains the application-side Kubernetes resources for the EKS path.
It does not install Sumo Logic or create AWS infrastructure.

## What The Manifests Do

| File | Purpose |
|---|---|
| `namespace.yaml` | Creates the `temporal-observability` namespace and sets a Sumo source category annotation. |
| `configmap.yaml` | Holds non-secret runtime settings for Temporal target, namespace, task queue, and app identity. |
| `secret-template.yaml` | Template for optional Temporal Cloud API key. Do not commit a filled version. |
| `deployment.yaml` | Runs the Spring Boot Temporal worker and exposes `/actuator/prometheus`. |
| `service.yaml` | Provides an internal ClusterIP service for app access. |

## Required Edits Before Apply

1. Replace the image in `deployment.yaml` with an image you pushed to ECR.
2. Replace `TEMPORAL_TARGET`, `TEMPORAL_NAMESPACE`, and `TEMPORAL_TASK_QUEUE` in `configmap.yaml`.
3. Use `secret-template.yaml` only if the app connects to Temporal Cloud with API-key auth.
4. Confirm the namespace and source category names match the Sumo dashboard variables.

For EKS nodes running `linux/amd64`, build and push an amd64 image explicitly from Apple silicon machines:

```bash
mvn clean package
docker buildx build \
  --platform linux/amd64 \
  -f docker/Dockerfile \
  -t <account-id>.dkr.ecr.<region>.amazonaws.com/temporal-java-sumo-observability:<tag> \
  --push .
```

## Collection Contract

The deployment exposes Spring Boot Actuator metrics on:

```text
/actuator/prometheus
```

The pod annotations are intentionally compatible with Sumo Kubernetes Collection v5 behavior for one-endpoint Prometheus scraping:

```yaml
prometheus.io/scrape: "true"
prometheus.io/path: /actuator/prometheus
prometheus.io/port: "8080"
```

The example Sumo log source category is `kubernetes/temporal/java/worker`. Confirm the category in the target Sumo account before finalizing log queries.

The worker metrics dashboard filters on the Sumo metric dimension `service=temporal-java-sumo-observability`.

## Approval Gate

Do not run `kubectl apply` against an EKS cluster until these items are approved:

- AWS account and region.
- EKS cluster name.
- ECR repository and image tag.
- Temporal target type: Temporal Cloud or self-hosted Temporal.
- Secret storage approach.
- Sumo source category naming.
