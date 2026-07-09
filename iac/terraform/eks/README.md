# EKS Terraform Template

This is an IaC template for an EKS evaluation cluster. It is intentionally not applied by this repo change.

## Design Choice

The template expects an existing VPC and subnet IDs. Private subnets are preferred for production-style evaluation. Public default subnets are acceptable only for a low-cost evaluation where NAT gateway cost is intentionally avoided.

It uses `terraform-aws-modules/eks/aws` with `version = "~> 21.0"`. The v21 module uses `name`, `kubernetes_version`, `endpoint_public_access`, `addons`, and `eks_managed_node_groups`.

## Approval Gate

Before any Terraform command beyond formatting or validation:

1. Confirm AWS account ID and region.
2. Confirm VPC and subnet IDs.
3. Confirm whether public EKS endpoint access is acceptable for the evaluation.
4. Restrict public EKS endpoint access to the current workstation IP as a `/32`.
5. Confirm estimated monthly cost for EKS control plane, EC2 nodes, NAT, EBS, and data transfer.
6. Confirm destroy plan and retention expectations.

## Local Commands

These commands are safe for syntax validation only. Do not run `apply` until approved.

```bash
cd iac/terraform/eks
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform validate
terraform plan
```

Do not commit `terraform.tfvars` if it contains real account or network IDs.

## After Cluster Creation

If approved and applied later, the next steps are:

1. Configure `kubectl` using the output command.
2. Install Sumo Kubernetes Collection from `sumo/kubernetes-collection-values.example.yaml`.
3. Build and push the app image to the ECR repository from the Terraform output.
4. Update `k8s/deployment.yaml` image and `k8s/configmap.yaml`.
5. Apply the Kubernetes manifests.
6. Import dashboards from `dashboards/sumo/`.
