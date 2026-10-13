provider "aws" {
  region              = var.aws_region
  allowed_account_ids = [var.expected_account_id]
  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "Terraform"
      Purpose     = "PortfolioDemo"
    }
  }
}
data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}
data "aws_availability_zones" "available" {
  state = "available"
}
data "aws_ec2_instance_type" "selected" {
  instance_type = var.instance_type
}
data "aws_ssm_parameter" "ubuntu_ami" {
  name = "/aws/service/canonical/ubuntu/server/24.04/stable/current/${local.ubuntu_architecture}/hvm/ebs-gp3/ami-id"
}
# Validate AMI architecture even when the operator supplies an explicit pin.
data "aws_ami" "selected" {
  filter {
    name   = "image-id"
    values = [var.ami_id != null ? var.ami_id : nonsensitive(data.aws_ssm_parameter.ubuntu_ami.value)]
  }
  filter {
    name   = "architecture"
    values = [var.instance_architecture]
  }
  filter {
    name   = "state"
    values = ["available"]
  }
}
