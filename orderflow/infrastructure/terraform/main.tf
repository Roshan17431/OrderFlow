# Hard failures (not advisory checks) before AWS resource creation.
resource "terraform_data" "deployment_guard" {
  input = "${var.expected_account_id}/${var.aws_region}/${var.environment}"
  lifecycle {
    precondition {
      condition     = data.aws_caller_identity.current.account_id == var.expected_account_id
      error_message = "AWS caller account differs from the explicitly selected deployment account."
    }
    precondition {
      condition     = contains(data.aws_ec2_instance_type.selected.supported_architectures, var.instance_architecture)
      error_message = "EC2 architecture and release image platform disagree."
    }
    precondition {
      condition     = contains(data.aws_availability_zones.available.names, local.availability_zone)
      error_message = "The selected AZ is unavailable or outside the selected region."
    }
    precondition {
      condition     = fileexists(local.bootstrap_path)
      error_message = "Required reviewed bootstrap script infrastructure/bootstrap/bootstrap-ec2.sh is missing."
    }
    precondition {
      condition     = !var.enable_route53 || (var.domain_name != "" && var.route53_zone_id != "" && var.enable_elastic_ip)
      error_message = "Route 53 requires an authorized hostname, existing zone and stable Elastic IP."
    }
    precondition {
      condition     = !var.enable_budget_alerts || can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.budget_notification_email))
      error_message = "Budget alerts require an explicitly authorized valid recipient."
    }
    precondition {
      condition     = var.github_oidc_provider_arn == null || var.github_oidc_provider_arn == "arn:${local.partition}:iam::${local.account_id}:oidc-provider/token.actions.githubusercontent.com"
      error_message = "Reuse only the GitHub OIDC provider belonging to the selected account. Variables cannot redirect trust to another provider."
    }
    precondition {
      condition     = var.github_publish_environment != var.github_deploy_environment
      error_message = "Image publishing and deployment must use distinct protected GitHub environments."
    }
  }
}
