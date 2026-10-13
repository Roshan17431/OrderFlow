resource "aws_iam_role" "application" {
  name = "${local.name}-ec2"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect    = "Allow"
    Action    = "sts:AssumeRole"
    Principal = { Service = "ec2.amazonaws.com" }
  }] })
  depends_on = [terraform_data.deployment_guard]
}
resource "aws_iam_instance_profile" "application" {
  name = "${local.name}-ec2"
  role = aws_iam_role.application.name
}
resource "aws_iam_role_policy" "ssm_agent" {
  name = "ssm-agent-channels"
  role = aws_iam_role.application.id
  # Channel and managed-node self-registration APIs have no resource-level
  # IAM support. No broad SSM GetParameters or account administration policy.
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    {
      Sid      = "AgentChannelsAndRegistration"
      Effect   = "Allow"
      Action   = ["ssm:UpdateInstanceInformation", "ssmmessages:CreateControlChannel", "ssmmessages:CreateDataChannel", "ssmmessages:OpenControlChannel", "ssmmessages:OpenDataChannel"]
      Resource = ["*"]
    },
    {
      Sid      = "ReadApprovedSessionAndCommandDocuments"
      Effect   = "Allow"
      Action   = ["ssm:GetDocument", "ssm:DescribeDocument"]
      Resource = ["arn:${local.partition}:ssm:${var.aws_region}::document/AWS-RunShellScript", "arn:${local.partition}:ssm:${var.aws_region}::document/AWS-StartPortForwardingSession"]
    }
  ] })
}
resource "aws_iam_role_policy" "ecr_pull" {
  name = "ecr-release-pull"
  role = aws_iam_role.application.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    {
      Effect   = "Allow"
      Action   = ["ecr:GetAuthorizationToken"]
      Resource = ["*"]
    },
    {
      Effect   = "Allow"
      Action   = ["ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage", "ecr:DescribeImages"]
      Resource = local.ecr_repository_arns
    }
  ] })
}
resource "aws_iam_role_policy" "backups" {
  name = "backup-and-release-transport"
  role = aws_iam_role.application.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    {
      Effect   = "Allow"
      Action   = ["s3:GetBucketLocation"]
      Resource = [aws_s3_bucket.backups.arn]
    },
    {
      Effect    = "Allow"
      Action    = ["s3:ListBucket"]
      Resource  = [aws_s3_bucket.backups.arn]
      Condition = { StringLike = { "s3:prefix" = ["backups/*", "releases/*"] } }
    },
    {
      Effect   = "Allow"
      Action   = ["s3:PutObject", "s3:GetObject", "s3:GetObjectVersion", "s3:AbortMultipartUpload", "s3:ListMultipartUploadParts"]
      Resource = ["${aws_s3_bucket.backups.arn}/backups/*"]
    },
    {
      Effect   = "Allow"
      Action   = ["s3:GetObject"]
      Resource = ["${aws_s3_bucket.backups.arn}/releases/*"]
    }
  ] })
}
resource "aws_iam_openid_connect_provider" "github" {
  count          = var.github_oidc_provider_arn == null ? 1 : 0
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  # IAM retrieves the IdP TLS thumbprint. Do not pin a stale copied certificate.
  depends_on = [terraform_data.deployment_guard]
}
resource "aws_iam_role" "github_publish" {
  name                 = "${local.name}-github-publish"
  max_session_duration = 3600
  depends_on           = [terraform_data.deployment_guard]
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect    = "Allow"
    Action    = "sts:AssumeRoleWithWebIdentity"
    Principal = { Federated = local.github_oidc_arn }
    Condition = { StringEquals = {
      "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
      "token.actions.githubusercontent.com:sub" = "repo:${var.github_repository}:environment:${var.github_publish_environment}"
    } }
  }] })
}
resource "aws_iam_role_policy" "github_publish" {
  name = "ecr-release-publish"
  role = aws_iam_role.github_publish.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    {
      Effect   = "Allow"
      Action   = ["ecr:GetAuthorizationToken"]
      Resource = ["*"]
    },
    {
      Effect = "Allow"
      Action = ["ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage",
        "ecr:InitiateLayerUpload", "ecr:UploadLayerPart", "ecr:CompleteLayerUpload", "ecr:PutImage",
      "ecr:DescribeImages", "ecr:DescribeRepositories"]
      Resource = local.ecr_repository_arns
    }
  ] })
}
resource "aws_iam_role" "github_deploy" {
  name                 = "${local.name}-github-deploy"
  max_session_duration = 3600
  depends_on           = [terraform_data.deployment_guard]
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect    = "Allow"
    Action    = "sts:AssumeRoleWithWebIdentity"
    Principal = { Federated = local.github_oidc_arn }
    Condition = { StringEquals = {
      "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
      "token.actions.githubusercontent.com:sub" = "repo:${var.github_repository}:environment:${var.github_deploy_environment}"
    } }
  }] })
}
resource "aws_iam_role_policy" "github_deploy" {
  name = "approved-instance-deployment"
  role = aws_iam_role.github_deploy.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    {
      Sid      = "ApprovedDeploymentDocument"
      Effect   = "Allow"
      Action   = ["ssm:SendCommand"]
      Resource = ["arn:${local.partition}:ssm:${var.aws_region}::document/AWS-RunShellScript"]
    },
    {
      Sid      = "ThisDeploymentInstanceOnly"
      Effect   = "Allow"
      Action   = ["ssm:SendCommand"]
      Resource = [aws_instance.application.arn]
      Condition = { StringEquals = {
        "ssm:resourceTag/Project" = var.project_name, "ssm:resourceTag/Environment" = var.environment
      } }
    },
    {
      Sid      = "BoundedCommandStatus"
      Effect   = "Allow"
      Action   = ["ssm:GetCommandInvocation"]
      Resource = ["*"] # This read API does not support resource-level permissions.
    },
    {
      Sid      = "UploadNonsecretReleaseArtifacts"
      Effect   = "Allow"
      Action   = ["s3:PutObject", "s3:AbortMultipartUpload", "s3:ListMultipartUploadParts"]
      Resource = ["${aws_s3_bucket.backups.arn}/releases/*"]
    },
    {
      Sid      = "VerifyImmutableImages"
      Effect   = "Allow"
      Action   = ["ecr:DescribeImages", "ecr:BatchGetImage"]
      Resource = local.ecr_repository_arns
    }
  ] })
}
