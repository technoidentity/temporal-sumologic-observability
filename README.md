# Temporal Java SDK + Sumo Logic Metrics and Logs Observability


This repository is organized around two paths:

1. EKS path: production-style evaluation using Sumo Logic Kubernetes Collection installed by Helm.
2. Docker path: local reproducibility and debugging for the Java SDK metrics, logs, dashboards, and Temporal Cloud metrics.

The EKS path is the recommended production-style evaluation path. Docker is retained so the code and dashboards can be tested before touching AWS or Sumo accounts.

Repository: `git@github.com:technoidentity/temporal-sumologic-observability.git`

## What This Covers

| Signal | Status | Path |
|---|---|---|
| Application logs | Implemented | stdout/stderr in Docker or Kubernetes container logs through Sumo collection. |
| Temporal Java SDK worker metrics | Implemented | SDK -> Tally -> Micrometer -> `/actuator/prometheus` -> Sumo scrape. |
| Kubernetes metrics and events | Configured for EKS path | Sumo Kubernetes Collection Helm chart. |
| Temporal Cloud metrics | Validated for EKS evaluation | Temporal Cloud OpenMetrics -> dedicated OTel Collector -> Sumo Helm OTLP metrics source. |
| Dashboards | Included | Importable Sumo dashboard JSON under `dashboards/sumo/`. |
| Scenario drivers | Implemented for validation | HTTP endpoints under `/temporal/scenarios/*` populate success, failure, timeout, cancel, terminate, backlog, schedule, and load panels. |
| CI readiness | Included | GitHub Actions workflow validates Maven tests, dashboard JSON, YAML syntax, Docker Compose config, and Terraform formatting/validation. |
| Alerts and monitors | Specification included | `sumo/production-monitor-specification.md` defines query logic, starting thresholds, recovery, grouping, ownership, and validation. Environment-specific Sumo monitor resources remain to be created after the target organization approves routing and thresholds. |

## Repository Layout

```text
.
|- README.md
|- LICENSE
|- SECURITY.md
|- CONTRIBUTING.md
|- .github/workflows/ci.yml
|- pom.xml
|- .env.example
|- src/
|- docs/
|  +- dashboard-scenario-drivers.md
|- docker/
|  |- Dockerfile
|  |- docker-compose.yml
|  |- otel-collector-config.yaml
|  +- README.md
|- k8s/
|  |- namespace.yaml
|  |- configmap.yaml
|  |- deployment.yaml
|  |- service.yaml
|  |- secret-template.yaml
|  +- README.md
|- sumo/
|  |- kubernetes-collection-values.example.yaml
|  |- metric-field-mapping.md
|  |- production-monitor-specification.md
|  |- validation-queries.md
|  |- temporal-cloud-otel-collector.yaml
|  |- temporal-cloud-otel-collector-secret-template.yaml
|  +- README.md
|- dashboards/sumo/
|  |- import-checklist.md
|  |- metric-to-panel-mapping.md
|  |- worker-sdk/worker-metrics-dashboard.json
|  +- temporal-cloud/temporal-cloud-metrics-dashboard.json
+- iac/terraform/eks/
   |- versions.tf
   |- main.tf
   |- variables.tf
   |- outputs.tf
   |- terraform.tfvars.example
   +- README.md
```

## EKS Evaluation Path

This path should be used when validating against an EKS setup.

No command in this section should be run until the AWS account, region, VPC/subnets, cost boundary, Sumo account, and cleanup plan are approved.

### 1. Create EKS With Terraform

Template:

```text
iac/terraform/eks/
```

The Terraform template expects an existing VPC and subnet IDs. Private subnets are preferred for production-style evaluation; public default subnets are acceptable only for a low-cost evaluation.

Validation-only commands:

```bash
cd iac/terraform/eks
terraform fmt -check
terraform init
terraform validate
terraform plan
```

Do not run `terraform apply` until approved.

### 2. Install Sumo Kubernetes Collection

Values template:

```text
sumo/kubernetes-collection-values.example.yaml
```

Install shape:

```bash
helm repo add sumologic https://sumologic.github.io/sumologic-kubernetes-collection
helm repo update

helm upgrade --install sumologic sumologic/sumologic \
  --namespace sumologic \
  --create-namespace \
  -f sumo/kubernetes-collection-values.example.yaml \
  --set sumologic.accessId="$SUMO_ACCESS_ID" \
  --set sumologic.accessKey="$SUMO_ACCESS_KEY" \
  --set sumologic.clusterName="$SUMO_CLUSTER_NAME"
```

Do not commit Sumo credentials or a filled private values file.

### 3. Deploy Temporal Cloud Metrics Scrape

Include Temporal Cloud service-side metrics in the reference architecture. The Sumo Kubernetes Collection chart does not automatically scrape `metrics.temporal.io`; this repo uses a small dedicated collector in the `sumologic` namespace and exports to the Helm chart's existing OTLP metrics source.

```bash
kubectl -n sumologic create secret generic temporal-cloud-otel-collector-secrets \
  --from-literal=TEMPORAL_CLOUD_METRICS_API_KEY="$TEMPORAL_CLOUD_METRICS_API_KEY"

kubectl apply -f sumo/temporal-cloud-otel-collector.yaml
```

The validated dashboard filter for this path is:

```text
service=temporal-cloud temporal_namespace=<temporal-cloud-namespace>
```

### 4. Deploy The App

Kubernetes manifests:

```text
k8s/
```

Required edits before apply:

- Replace the image in `k8s/deployment.yaml` with the ECR image tag.
- Replace `TEMPORAL_TARGET`, `TEMPORAL_NAMESPACE`, and `TEMPORAL_TASK_QUEUE` in `k8s/configmap.yaml`.
- Create a real Kubernetes Secret only if the app connects to Temporal Cloud with API-key auth.
- Confirm Sumo source category and dashboard variable values.

Apply shape:

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret-template.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

Do not apply `secret-template.yaml` with placeholder values.

## Docker Local Validation

The Docker lane validates the app, collector config, and dashboards without EKS.

```bash
mvn clean package
docker compose -f docker/docker-compose.yml up --build -d
curl "http://localhost:8080/temporal/hello?name=Sumo"
curl -s http://localhost:8080/actuator/prometheus | grep '^temporal_'
```

Local endpoints:

| Endpoint | Purpose |
|---|---|
| `http://localhost:8080/temporal/hello?name=Sumo` | Starts a workflow. |
| `http://localhost:8080/temporal/scenarios/<scenario>` | Starts dashboard validation scenarios. |
| `http://localhost:8080/actuator/prometheus` | Exposes worker and app metrics. |
| `http://localhost:8233` | Local Temporal UI. |

Scenario drivers are documented in `docs/dashboard-scenario-drivers.md`.

## Dashboards

Importable dashboards:

| Dashboard | File |
|---|---|
| Worker SDK metrics | `dashboards/sumo/worker-sdk/worker-metrics-dashboard.json` |
| Temporal Cloud metrics | `dashboards/sumo/temporal-cloud/temporal-cloud-metrics-dashboard.json` |

The worker dashboard uses a `Service` variable for SDK metrics. Default value:

```text
temporal-java-sumo-observability
```

The EKS log validation category observed for this evaluation is:

```text
kubernetes/temporal/java/worker
```

The Temporal Cloud dashboard uses a `Cloud Service` variable. Default value:

```text
temporal-cloud
```

Do not use `_sourceCategory` as the primary Cloud dashboard selector for the EKS path. The Sumo OTLP HTTP source category can vary by account; `service=temporal-cloud` is the stable dimension set by the collector.

Validate these values in the target Sumo account before publishing dashboards to a shared folder.

## Recommended Metrics To Track

| Area | Metrics |
|---|---|
| Workflow throughput | `temporal_workflow_completed_total` |
| Workflow latency | `temporal_workflow_endtoend_latency_seconds_sum`, `temporal_workflow_endtoend_latency_seconds_count` |
| Workflow task pickup | `temporal_workflow_task_schedule_to_start_latency_seconds_*` |
| Activity execution | `temporal_activity_execution_latency_seconds_*` |
| Activity pickup delay | `temporal_activity_schedule_to_start_latency_seconds_*` |
| Worker saturation | `temporal_worker_task_slots_used`, `temporal_worker_task_slots_available` |
| Worker lifecycle | `temporal_worker_start_total`, `temporal_poller_start_total`, `temporal_worker_task_slots_available` |
| SDK failures | `temporal_request_failure_total`, `temporal_request_latency_seconds_*` |
| Workflow task failures | `temporal_workflow_task_execution_failed_total` |
| Activity failures | `temporal_activity_execution_failed_total`, `temporal_local_activity_execution_failed_total` when local activities are used |
| Sticky cache | `temporal_sticky_cache_hit_total`, `temporal_sticky_cache_size`, `temporal_sticky_cache_total_forced_eviction_total` |
| Temporal Cloud backlog | `temporal_cloud_v1_approximate_backlog_count`, `temporal_cloud_v1_no_poller_tasks_count` |
| Temporal Cloud service health | `temporal_cloud_v1_service_request_count`, `temporal_cloud_v1_service_error_count`, `temporal_cloud_v1_service_latency_p95` |
| Temporal Cloud throttling | `temporal_cloud_v1_service_request_throttled_count`, `temporal_cloud_v1_operations_throttled_count`, `temporal_cloud_v1_total_action_throttled_count`, `temporal_cloud_v1_resource_exhausted_error_count` |
| Temporal Cloud limits | `temporal_cloud_v1_action_limit`, `temporal_cloud_v1_service_request_limit` |
| Temporal Cloud schedules | `temporal_cloud_v1_schedule_action_success_count`, `temporal_cloud_v1_schedule_buffer_overruns_count`, `temporal_cloud_v1_schedule_missed_catchup_window_count`, `temporal_cloud_v1_schedule_rate_limited_count` |

The Java SDK does not emit `temporal_num_pollers`. Do not use that metric for a Java worker-availability dashboard or monitor. Determine worker availability from Kubernetes readiness/replica state and corroborate it with Temporal Cloud no-poller tasks and backlog. `temporal_poller_start_total` is a lifecycle counter, not a current-poller gauge.

## Configuration

Copy `.env.example` to `.env` for Docker validation only:

```bash
cp .env.example .env
```

### Security Considerations

**Actuator Endpoint Security**: The worker exposes `/actuator/prometheus` with unrestricted access for in-cluster scraping by Sumo Logic. This endpoint MUST remain network-restricted and never be exposed through ingress, LoadBalancer, or external services in production. It exposes internal metrics that could leak operational information.

In Kubernetes deployments, the actuator endpoint is only accessible within the cluster through the Service resource. Do not create an Ingress or LoadBalancer service pointing to this endpoint in production environments.

### Configuration Variables

| Variable | Purpose |
|---|---|
| `SERVICE_NAME` | App/service identity used in Spring and metrics tags. |
| `DEPLOYMENT_ENVIRONMENT` | `docker-local`, `eks`, or another environment label. |
| `SUMO_ACCESS_ID` | Sumo Access ID used by the Helm and API validation paths. |
| `SUMO_ACCESS_KEY` | Sumo Access Key used by the Helm and API validation paths. |
| `SUMOLOGIC_OTLP_SOURCE_ENDPOINT` | Existing Sumo OTLP HTTP source endpoint used by the optional local Docker exporter. |
| `TEMPORAL_TARGET` | Temporal frontend address. |
| `TEMPORAL_NAMESPACE` | Temporal namespace. |
| `TEMPORAL_TASK_QUEUE` | Worker and workflow task queue. |
| `TEMPORAL_API_KEY` | Temporal Cloud worker/client API key when the worker connects to Temporal Cloud. |
| `TEMPORAL_CLOUD_METRICS_API_KEY` | Temporal Cloud Metrics Read-Only key for `temporal_cloud_v1_*` dashboard validation. |

## Verification

Run local checks:

```bash
mvn test
python3 -m json.tool dashboards/sumo/worker-sdk/worker-metrics-dashboard.json >/tmp/worker-metrics-dashboard.json
python3 -m json.tool dashboards/sumo/temporal-cloud/temporal-cloud-metrics-dashboard.json >/tmp/temporal-cloud-metrics-dashboard.json
docker compose -f docker/docker-compose.yml config >/tmp/docker-compose-rendered.yaml
ruby -e 'require "yaml"; ARGV.each { |file| YAML.load_file(file); puts "validated #{file}" }' \
  $(find .github docker k8s sumo iac -path "*/.terraform/*" -prune -o -type f \( -name "*.yaml" -o -name "*.yml" \) -print | sort)
terraform -chdir=iac/terraform/eks fmt -check
terraform -chdir=iac/terraform/eks init -backend=false
terraform -chdir=iac/terraform/eks validate
```

## References

- Sumo Logic Kubernetes Helm chart installation: https://www.sumologic.com/help/docs/send-data/kubernetes/install-helm-chart/
- Sumo Logic Kubernetes metrics collection: https://www.sumologic.com/help/docs/send-data/kubernetes/collecting-metrics/
- Sumo Logic Kubernetes best practices: https://www.sumologic.com/help/docs/send-data/kubernetes/best-practices/
- Terraform AWS EKS module: https://registry.terraform.io/modules/terraform-aws-modules/eks/aws/latest
