# Contributing

This repository is currently maintained as an evaluation sample. Keep changes small, reproducible, and free of account-specific values.

## Before Submitting Changes

Run:

```bash
mvn test
python3 -m json.tool dashboards/sumo/worker-sdk/worker-metrics-dashboard.json >/tmp/worker-dashboard.json
python3 -m json.tool dashboards/sumo/temporal-cloud/temporal-cloud-metrics-dashboard.json >/tmp/cloud-dashboard.json
docker compose -f docker/docker-compose.yml config >/tmp/docker-compose-rendered.yaml
ruby -e 'require "yaml"; ARGV.each { |file| YAML.load_file(file); puts "validated #{file}" }' \
  $(find .github docker k8s sumo iac -path "*/.terraform/*" -prune -o -type f \( -name "*.yaml" -o -name "*.yml" \) -print | sort)
terraform -chdir=iac/terraform/eks fmt -check
terraform -chdir=iac/terraform/eks init -backend=false
terraform -chdir=iac/terraform/eks validate
```

## Change Guidelines

- Keep Docker/local validation separate from EKS validation.
- Keep dashboards under `dashboards/sumo/`.
- Keep account, namespace, and task-queue values configurable.
- Do not add a new ingestion path unless the README and project overview explain when to use it.
- Add validation evidence when dashboard queries or metric mappings change.
- Do not claim traces, monitors, or production readiness until those pieces are implemented and validated.
