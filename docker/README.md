# Docker Local Validation

The Docker lane is a local validation harness. It is not the recommended production path.

## Run

```bash
mvn clean package
docker compose -f docker/docker-compose.yml up --build -d
```

When building for EKS from Apple silicon, do not reuse the local Docker image unless it was built for `linux/amd64`. Use the Kubernetes README build command for the ECR image.

Trigger a workflow:

```bash
curl "http://localhost:8080/temporal/hello?name=Sumo"
```

Check worker metrics:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep '^temporal_'
```

## Local Services

| Service | URL |
|---|---|
| App endpoint | `http://localhost:8080/temporal/hello?name=Sumo` |
| Prometheus metrics | `http://localhost:8080/actuator/prometheus` |
| Temporal UI | `http://localhost:8233` |

## Collection

`otel-collector-config.yaml` scrapes the app and optionally scrapes Temporal Cloud OpenMetrics if the Cloud metrics key is set.

For EKS, use `../sumo/kubernetes-collection-values.example.yaml` and the Sumo Helm chart instead.
