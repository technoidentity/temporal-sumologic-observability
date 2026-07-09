variable "aws_region" {
  description = "AWS region where EKS will be created."
  type        = string
}

variable "aws_profile" {
  description = "Optional AWS CLI profile. Use this for SSO-backed local evaluation."
  type        = string
  default     = null
}

variable "cluster_name" {
  description = "EKS cluster name."
  type        = string
}

variable "app_repository_name" {
  description = "ECR repository name for the Spring Boot Temporal worker image."
  type        = string
  default     = "temporal-java-sumo-observability"
}

variable "kubernetes_version" {
  description = "EKS Kubernetes version. Verify current AWS support before apply."
  type        = string
  default     = "1.33"
}

variable "cloudwatch_log_group_retention_in_days" {
  description = "Retention period for EKS control plane logs."
  type        = number
  default     = 7
}

variable "vpc_id" {
  description = "Existing VPC ID. This module intentionally does not create networking."
  type        = string
}

variable "subnet_ids" {
  description = "Subnet IDs for EKS worker nodes. Private subnets are preferred; public default subnets are acceptable only for low-cost evaluation."
  type        = list(string)
}

variable "endpoint_public_access" {
  description = "Whether the EKS API endpoint is publicly reachable. Use private access for production when network access is available."
  type        = bool
  default     = true
}

variable "endpoint_public_access_cidrs" {
  description = "CIDR ranges allowed to reach the public EKS API endpoint. Use a narrow CIDR such as a workstation /32 for evaluation."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "node_instance_types" {
  description = "Instance types for the default managed node group."
  type        = list(string)
  default     = ["m6i.large"]
}

variable "node_min_size" {
  description = "Minimum worker node count."
  type        = number
  default     = 1
}

variable "node_desired_size" {
  description = "Desired worker node count."
  type        = number
  default     = 2
}

variable "node_max_size" {
  description = "Maximum worker node count."
  type        = number
  default     = 3
}

variable "tags" {
  description = "Tags applied to AWS resources."
  type        = map(string)
  default = {
    Project     = "temporal-java-sumo-observability"
    ManagedBy   = "terraform"
    Environment = "evaluation"
  }
}
