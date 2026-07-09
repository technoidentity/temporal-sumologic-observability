# Security

## Supported Status

This repository is an evaluation sample, not a production deployment package.

## Sensitive Data Rules

- Do not commit Sumo Logic access IDs, access keys, source endpoints, or API keys.
- Do not commit Temporal Cloud API keys, metrics API keys, namespace-specific secrets, or TLS material.
- Do not commit AWS credentials, Terraform state, kubeconfig files, or filled `terraform.tfvars`.
- Do not commit screenshots or exported evidence that expose customer, tenant, account, namespace, or workflow payload data.
- Keep `.env`, private Helm values, and generated validation output local.

## Reporting

Report security issues privately to the repository owner or engagement lead. Do not open a public issue with credentials, tenant details, payload data, or infrastructure identifiers.

## Production Hardening Not Included

Before adapting this sample for production, review:

- secret management through the approved enterprise mechanism;
- network egress and ingress controls;
- payload and log redaction;
- Sumo role-based access controls;
- monitor routing and incident ownership;
- retention and compliance requirements;
- Terraform state storage and access controls.
