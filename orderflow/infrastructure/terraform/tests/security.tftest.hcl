# Offline plans with mocked AWS: never creates resources or contacts an account.
mock_provider "aws" {}

variables {
  expected_account_id      = "123456789012"
  github_oidc_provider_arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
}

override_data {
  target = data.aws_caller_identity.current
  values = { account_id = "123456789012" }
}
override_data {
  target = data.aws_partition.current
  values = { partition = "aws", dns_suffix = "amazonaws.com" }
}
override_data {
  target = data.aws_availability_zones.available
  values = { names = ["us-east-1a"] }
}
override_data {
  target = data.aws_ssm_parameter.ubuntu_ami
  values = { value = "ami-0123456789abcdef0" }
}
override_data {
  target = data.aws_ec2_instance_type.selected
  values = { supported_architectures = ["x86_64"], memory_size = 16384 }
}

run "secure_default_plan" {
  command = plan

  assert {
    condition     = toset([for r in aws_vpc_security_group_ingress_rule.web : r.from_port]) == toset([80, 443]) && alltrue([for r in aws_vpc_security_group_ingress_rule.web : r.ip_protocol == "tcp" && r.from_port == r.to_port])
    error_message = "Only HTTP and HTTPS may be admitted by the public security group."
  }
  assert {
    condition     = aws_instance.application.root_block_device[0].encrypted && aws_ebs_volume.application_data.encrypted && aws_instance.application.metadata_options[0].http_tokens == "required"
    error_message = "Both disks must be encrypted; IMDSv2 is required."
  }
  assert {
    condition     = length(aws_ecr_repository.application) == 12 && alltrue([for r in aws_ecr_repository.application : r.image_tag_mutability == "IMMUTABLE" && !r.force_delete])
    error_message = "All 12 images require immutable ECR repositories and retained images."
  }
  assert {
    condition     = alltrue([for p in aws_ecr_lifecycle_policy.application : jsondecode(p.policy).rules[0].selection.tagStatus == "untagged"])
    error_message = "Lifecycle must never expire release-tagged images."
  }
  assert {
    condition     = aws_s3_bucket_public_access_block.backups.block_public_acls && aws_s3_bucket_public_access_block.backups.block_public_policy && aws_s3_bucket_public_access_block.backups.ignore_public_acls && aws_s3_bucket_public_access_block.backups.restrict_public_buckets && !aws_s3_bucket.backups.force_destroy
    error_message = "Backup and release bucket must block all public access and refuse forced deletion."
  }
  assert {
    condition     = length(aws_route53_record.application) == 0 && length(aws_eip.application) == 0 && length(aws_budgets_budget.monthly) == 0 && length(aws_cloudwatch_metric_alarm.cpu) == 0
    error_message = "Optional billable integrations must be disabled by default."
  }
  assert {
    condition     = jsondecode(aws_iam_role.github_publish.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == "repo:Roshan17431/OrderFlow:environment:cloud-demo-publish" && jsondecode(aws_iam_role.github_deploy.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == "repo:Roshan17431/OrderFlow:environment:cloud-demo"
    error_message = "Publish and deploy trust must be distinct exact GitHub environments."
  }
  assert {
    condition     = local.runtime_parameter_prefix == "/orderflow/cloud-demo/runtime/" && length([for s in jsondecode(aws_iam_role_policy.runtime_secrets.policy).Statement : s if contains(s.Action, "ssm:PutParameter")]) == 0
    error_message = "The instance may only read this deployment's parameter subtree."
  }
}

run "reject_wrong_account" {
  command = plan
  override_data {
    target = data.aws_caller_identity.current
    values = { account_id = "999999999999" }
  }
  expect_failures = [terraform_data.deployment_guard]
}

run "reject_architecture_mismatch" {
  command = plan
  variables { instance_architecture = "arm64" }
  expect_failures = [terraform_data.deployment_guard]
}

run "reject_incomplete_route53_authorization" {
  command = plan
  variables {
    enable_route53 = true
  }
  expect_failures = [terraform_data.deployment_guard]
}

run "reject_wrong_oidc_account" {
  command = plan
  variables {
    github_oidc_provider_arn = "arn:aws:iam::999999999999:oidc-provider/token.actions.githubusercontent.com"
  }
  expect_failures = [terraform_data.deployment_guard]
}

run "optional_approved_integrations" {
  command = plan
  variables {
    enable_elastic_ip         = true
    enable_route53            = true
    domain_name               = "orderflow.example.com"
    route53_zone_id           = "Z000000000EXAMPLE"
    enable_observability      = true
    alarm_notification_email  = "operator@example.com"
    enable_budget_alerts      = true
    budget_notification_email = "operator@example.com"
    runtime_kms_key_arn       = "arn:aws:kms:us-east-1:123456789012:key/11111111-1111-1111-1111-111111111111"
  }
  assert {
    condition     = length(aws_eip.application) == 1 && length(aws_route53_record.application) == 1 && !aws_route53_record.application[0].allow_overwrite && length(aws_cloudwatch_metric_alarm.status) == 1 && length(aws_cloudwatch_metric_alarm.cpu) == 1 && aws_cloudwatch_log_group.host[0].retention_in_days == 14 && length(aws_budgets_budget.monthly) == 1
    error_message = "Explicit optional features must preserve safe DNS overwrite and bounded log retention defaults."
  }
  assert {
    condition     = jsondecode(aws_iam_role_policy.runtime_secrets.policy).Statement[1].Action == ["kms:Decrypt"] && jsondecode(aws_iam_role_policy.runtime_secrets.policy).Statement[1].Condition.StringLike["kms:EncryptionContext:PARAMETER_ARN"] == local.runtime_parameter_arn
    error_message = "CMK permission must be decrypt-only and constrained to the runtime subtree."
  }
}

run "publisher_workflow_permission_contract" {
  command = plan
  override_resource {
    target          = aws_ecr_repository.application
    override_during = plan
    values = {
      arn = "arn:aws:ecr:us-east-1:123456789012:repository/orderflow/offline-policy-fixture"
    }
  }
  assert {
    condition     = contains(flatten([for statement in jsondecode(aws_iam_role_policy.github_publish.policy).Statement : statement.Action]), "ecr:DescribeRepositories")
    error_message = "Image publishing workflow must be able to verify immutable repository settings before pushing."
  }
  assert {
    condition     = !contains(flatten([for statement in jsondecode(aws_iam_role_policy.github_publish.policy).Statement : statement.Action]), "ecr:DeleteRepository") && !contains(flatten([for statement in jsondecode(aws_iam_role_policy.github_publish.policy).Statement : statement.Action]), "ecr:BatchDeleteImage")
    error_message = "Image publication must not be able to delete a release repository or referenced image."
  }
}

run "new_github_oidc_provider" {
  command = plan
  variables {
    github_oidc_provider_arn = null
  }
  assert {
    condition     = length(aws_iam_openid_connect_provider.github) == 1 && aws_iam_openid_connect_provider.github[0].url == "https://token.actions.githubusercontent.com" && aws_iam_openid_connect_provider.github[0].client_id_list == toset(["sts.amazonaws.com"])
    error_message = "Fresh-account OIDC must trust only GitHub's exact issuer and STS audience."
  }
}

run "reject_shared_github_environment" {
  command = plan
  variables {
    github_publish_environment = "cloud-demo"
  }
  expect_failures = [terraform_data.deployment_guard]
}

run "reject_budget_without_recipient" {
  command = plan
  variables {
    enable_budget_alerts = true
  }
  expect_failures = [terraform_data.deployment_guard]
}
