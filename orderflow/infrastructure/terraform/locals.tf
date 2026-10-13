locals {
  name                       = "${var.project_name}-${var.environment}"
  account_id                 = var.expected_account_id
  partition                  = data.aws_partition.current.partition
  availability_zone          = coalesce(var.availability_zone, data.aws_availability_zones.available.names[0])
  ubuntu_architecture        = var.instance_architecture == "x86_64" ? "amd64" : "arm64"
  container_platform         = "linux/${local.ubuntu_architecture}"
  runtime_parameter_prefix   = "/${var.project_name}/${var.environment}/runtime/"
  runtime_parameter_path_arn = "arn:${local.partition}:ssm:${var.aws_region}:${local.account_id}:parameter${trimsuffix(local.runtime_parameter_prefix, "/")}"
  runtime_parameter_arn      = "arn:${local.partition}:ssm:${var.aws_region}:${local.account_id}:parameter${local.runtime_parameter_prefix}*"
  services = toset([
    "service-registry", "api-gateway", "auth-service", "product-service",
    "cart-service", "inventory-service", "order-service", "payment-service",
    "delivery-service", "notification-service", "operations-service", "frontend"
  ])
  ecr_repository_arns = [for r in aws_ecr_repository.application : r.arn]
  github_oidc_arn     = var.github_oidc_provider_arn != null ? var.github_oidc_provider_arn : aws_iam_openid_connect_provider.github[0].arn
  bootstrap_path      = "${path.module}/../bootstrap/bootstrap-ec2.sh"
}
