variable "expected_account_id" {
  description = "Required authorized AWS account ID; provider refuses any other account."
  type        = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.expected_account_id))
    error_message = "Supply an explicit 12-digit authorized account ID."
  }
}
variable "project_name" {
  type    = string
  default = "orderflow"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,19}$", var.project_name))
    error_message = "Project must be 3–20 lowercase letters, digits or hyphens."
  }
}
variable "environment" {
  type    = string
  default = "cloud-demo"
  validation {
    condition     = contains(["cloud-demo", "staging", "production"], var.environment)
    error_message = "Select cloud-demo, staging or production explicitly. Production application providers must be supported separately."
  }
}
variable "aws_region" {
  type    = string
  default = "us-east-1"
  validation {
    condition     = can(regex("^[a-z]{2}(-[a-z]+)+-[0-9]+$", var.aws_region))
    error_message = "Use a valid AWS region identifier."
  }
}
variable "availability_zone" {
  type        = string
  default     = null
  description = "Optional explicit AZ; instance and retained EBS must remain in the same AZ."
}
variable "vpc_cidr" {
  type    = string
  default = "10.42.0.0/16"
  validation {
    condition     = can(cidrnetmask(var.vpc_cidr))
    error_message = "Use an IPv4 CIDR for the private VPC address space."
  }
}
variable "subnet_cidr" {
  type    = string
  default = "10.42.1.0/24"
  validation {
    condition     = can(cidrnetmask(var.subnet_cidr))
    error_message = "Use an IPv4 subnet CIDR inside vpc_cidr."
  }
}
variable "instance_type" {
  type        = string
  default     = "t3.xlarge"
  description = "16 GiB x86 evaluation target. Measure full-stack memory and burst-credit usage before selecting a smaller host."
}
variable "instance_architecture" {
  type    = string
  default = "x86_64"
  validation {
    condition     = contains(["x86_64", "arm64"], var.instance_architecture)
    error_message = "Architecture must be x86_64 or arm64; it must match the selected instance and release images."
  }
}
variable "ami_id" {
  type        = string
  default     = null
  description = "Optional approved Ubuntu 24.04 AMI pin. Otherwise resolve Canonical's stable public SSM AMI."
  validation {
    condition     = var.ami_id == null || can(regex("^ami-[0-9a-f]{8,17}$", var.ami_id))
    error_message = "Supply a valid approved AMI ID or null."
  }
}
variable "root_volume_size" {
  type    = number
  default = 30
  validation {
    condition     = var.root_volume_size >= 20 && floor(var.root_volume_size) == var.root_volume_size
    error_message = "Root disk must be at least 20 GiB and an integer."
  }
}
variable "data_volume_size" {
  type    = number
  default = 100
  validation {
    condition     = var.data_volume_size >= 30 && floor(var.data_volume_size) == var.data_volume_size
    error_message = "Persistent disk must be at least 30 GiB and an integer."
  }
}
variable "ebs_kms_key_arn" {
  type        = string
  default     = null
  description = "Optional existing EBS customer-managed key. No key or key material is created here."
}
variable "data_snapshot_id" {
  type        = string
  default     = null
  description = "Optional explicitly approved restore snapshot. Never automatically restores a running database."
}
variable "runtime_kms_key_arn" {
  type        = string
  default     = null
  description = "Optional existing CMK used by SSM SecureString; instance gets constrained Decrypt only."
}
variable "domain_name" {
  type    = string
  default = ""
  validation {
    condition     = var.domain_name == "" || can(regex("^([a-z0-9][a-z0-9-]*\\.)+[a-z]{2,63}$", var.domain_name))
    error_message = "Use a bare lowercase DNS hostname without scheme/path, or leave empty while awaiting DNS authorization."
  }
}
variable "enable_route53" {
  type        = bool
  default     = false
  description = "Enable only after explicit authorization to create this record in an existing hosted zone."
}
variable "route53_zone_id" {
  type    = string
  default = ""
}
variable "enable_elastic_ip" {
  type        = bool
  default     = false
  description = "Optional stable public IP, subject to explicit cost authorization. IPv4 is billed even without EIP."
}
variable "enable_observability" {
  type        = bool
  default     = false
  description = "Create optional EC2 CPU/status alarms and a bounded-retention CloudWatch host log group. Ingestion requires separately configured agent."
}
variable "alarm_notification_email" {
  type        = string
  default     = ""
  description = "Optional authorized SNS recipient; blank means alarms have no delivery action. Confirmation is required."
  validation {
    condition     = var.alarm_notification_email == "" || can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.alarm_notification_email))
    error_message = "Supply a valid email recipient or blank."
  }
}
variable "cloudwatch_log_retention_days" {
  type    = number
  default = 14
  validation {
    condition     = contains([7, 14, 30, 60, 90], var.cloudwatch_log_retention_days)
    error_message = "Select bounded retention: 7, 14, 30, 60 or 90 days."
  }
}
variable "enable_budget_alerts" {
  type        = bool
  default     = false
  description = "Opt-in email cost alert; it is not a hard spending cap."
}
variable "budget_notification_email" {
  type    = string
  default = ""
}
variable "monthly_budget_usd" {
  type        = number
  default     = 150
  description = "Operator-selected alert threshold, not a verified estimate or spending cap."
  validation {
    condition     = var.monthly_budget_usd > 0
    error_message = "Budget threshold must be positive."
  }
}
variable "backup_retention_days" {
  type        = number
  default     = 30
  description = "Explicit S3 backup current-object expiration; versions retained additional 90 days. Release objects are not expired."
  validation {
    condition     = var.backup_retention_days >= 7 && floor(var.backup_retention_days) == var.backup_retention_days
    error_message = "Backup retention must be an integer of at least 7 days."
  }
}
variable "github_repository" {
  type    = string
  default = "Roshan17431/OrderFlow"
  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "Use the exact GitHub OWNER/REPOSITORY."
  }
}
variable "github_publish_environment" {
  type    = string
  default = "cloud-demo-publish"
  validation {
    condition     = can(regex("^[a-z0-9-]+$", var.github_publish_environment))
    error_message = "Use an exact lowercase GitHub environment name without OIDC subject separators."
  }
}
variable "github_deploy_environment" {
  type    = string
  default = "cloud-demo"
  validation {
    condition     = can(regex("^[a-z0-9-]+$", var.github_deploy_environment))
    error_message = "Use an exact lowercase GitHub environment name without OIDC subject separators."
  }
}
variable "github_oidc_provider_arn" {
  type        = string
  default     = null
  description = "Reuse an existing token.actions.githubusercontent.com IAM OIDC provider rather than trying to create a duplicate."
}
