# Deliberately no aws_ssm_parameter / aws_secretsmanager_secret_version
# resources or secret-value data sources. SecureString values and persistent
# signing keys are provisioned by the operator OUTSIDE Terraform/state.
# Only the EC2 role can read the exact runtime parameter hierarchy.
resource "aws_iam_role_policy" "runtime_secrets" {
  name = "runtime-secret-read"
  role = aws_iam_role.application.id
  policy = jsonencode({ Version = "2012-10-17", Statement = concat([
    {
      Sid      = "ReadRuntimeParameters"
      Effect   = "Allow"
      Action   = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
      Resource = [local.runtime_parameter_path_arn, local.runtime_parameter_arn]
    }
    ], var.runtime_kms_key_arn == null ? [] : [{
      Sid      = "DecryptRuntimeSecureStrings"
      Effect   = "Allow"
      Action   = ["kms:Decrypt"]
      Resource = [var.runtime_kms_key_arn]
      Condition = {
        StringEquals = { "kms:ViaService" = "ssm.${var.aws_region}.${data.aws_partition.current.dns_suffix}" }
        StringLike   = { "kms:EncryptionContext:PARAMETER_ARN" = local.runtime_parameter_arn }
      }
  }]) })
}
