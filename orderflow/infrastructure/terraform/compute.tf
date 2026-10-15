resource "aws_ebs_volume" "application_data" {
  availability_zone = local.availability_zone
  size              = var.data_volume_size
  type              = "gp3"
  encrypted         = true
  kms_key_id        = var.ebs_kms_key_arn
  snapshot_id       = var.data_snapshot_id
  tags              = { Name = "${local.name}-retained-data" }
  lifecycle {
    prevent_destroy = true
  }
  depends_on = [terraform_data.deployment_guard]
}
resource "aws_instance" "application" {
  ami                                  = data.aws_ami.selected.id
  instance_type                        = var.instance_type
  availability_zone                    = local.availability_zone
  subnet_id                            = aws_subnet.public.id
  vpc_security_group_ids               = [aws_security_group.application.id]
  associate_public_ip_address          = true
  iam_instance_profile                 = aws_iam_instance_profile.application.name
  monitoring                           = false
  instance_initiated_shutdown_behavior = "stop"
  # Prevent automatic host replacement on bootstrap edits: rerun the reviewed
  # idempotent script through SSM after explicit operational approval.
  user_data_replace_on_change = false
  user_data_base64 = base64gzip(join("\n", ["#cloud-config", yamlencode({
    write_files = concat([{
      path        = "/usr/local/sbin/orderflow-bootstrap"
      permissions = "0700"
      owner       = "root:root"
      encoding    = "b64"
      content     = fileexists(local.bootstrap_path) ? filebase64(local.bootstrap_path) : ""
    }], local.on_demand_files)
    runcmd = [["env", "DATA_DEVICE=/dev/sdf", "DATA_MOUNT=/opt/orderflow",
      "EBS_VOLUME_ID=${aws_ebs_volume.application_data.id}",
      "ORDERFLOW_ENABLE_ON_DEMAND=${var.enable_on_demand}",
      "ALLOW_FORMAT_EMPTY_VOLUME=true", "EBS_ENCRYPTION_VERIFIED=true",
    "/bin/bash", "/usr/local/sbin/orderflow-bootstrap"]]
  })]))
  root_block_device {
    volume_size           = var.root_volume_size
    volume_type           = "gp3"
    encrypted             = true
    kms_key_id            = var.ebs_kms_key_arn
    delete_on_termination = true
  }
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "disabled"
  }
  dynamic "credit_specification" {
    for_each = can(regex("^t[234]", var.instance_type)) ? [1] : []
    content {
      cpu_credits = "standard"
    }
  }
  tags = { Name = local.name, OrderFlowOnDemand = tostring(var.enable_on_demand) }
  depends_on = [
    aws_route_table_association.public,
    aws_iam_role_policy.ssm_agent,
    aws_iam_role_policy.runtime_secrets,
    aws_iam_role_policy.ecr_pull,
    aws_iam_role_policy.backups,
    aws_iam_role_policy.on_demand_read
  ]
}
resource "aws_volume_attachment" "application_data" {
  device_name                    = "/dev/sdf"
  volume_id                      = aws_ebs_volume.application_data.id
  instance_id                    = aws_instance.application.id
  stop_instance_before_detaching = true
}
