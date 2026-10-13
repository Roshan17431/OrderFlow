output "deployment_identity" {
  value = { account_id = var.expected_account_id, region = var.aws_region, environment = var.environment }
}
output "instance_id" {
  value = aws_instance.application.id
}
output "public_ip" {
  value = var.enable_elastic_ip ? aws_eip.application[0].public_ip : aws_instance.application.public_ip
}
output "data_volume_id" {
  value = aws_ebs_volume.application_data.id
}
output "container_platform" {
  value = local.container_platform
}
output "ecr_registry" {
  value = "${local.account_id}.dkr.ecr.${var.aws_region}.${data.aws_partition.current.dns_suffix}"
}
output "ecr_repositories" {
  value = { for name, r in aws_ecr_repository.application : name => r.repository_url }
}
output "github_publish_role_arn" {
  value = aws_iam_role.github_publish.arn
}
output "github_deploy_role_arn" {
  value = aws_iam_role.github_deploy.arn
}
output "backup_bucket" {
  value = aws_s3_bucket.backups.id
}
output "release_bucket" {
  value = aws_s3_bucket.backups.id
}
output "runtime_parameter_prefix" {
  value = local.runtime_parameter_prefix
}
output "intended_https_origin" {
  description = "Intended origin only; not proof of DNS, certificate issuance or deployment."
  value       = var.domain_name == "" ? null : "https://${var.domain_name}"
}
