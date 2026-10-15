# Local asset preparation only: no schedule starts EC2 and no Lambda is required.
locals {
  on_demand_sources = {
    "/usr/local/lib/orderflow/on_demand.py"                     = "${path.module}/../../scripts/cloud/on_demand.py"
    "/usr/local/lib/orderflow/on_demand_host.py"                = "${path.module}/../../scripts/cloud/on_demand_host.py"
    "/etc/systemd/system/orderflow-demo.service"                = "${path.module}/../systemd/orderflow-demo.service"
    "/etc/systemd/system/orderflow-demo-stop.service"           = "${path.module}/../systemd/orderflow-demo-stop.service"
    "/etc/systemd/system/orderflow-demo-stop.timer"             = "${path.module}/../systemd/orderflow-demo-stop.timer"
    "/etc/systemd/system/docker.service.d/orderflow-mount.conf" = "${path.module}/../systemd/docker-orderflow-mount.conf"
  }
  on_demand_files = var.enable_on_demand ? concat([for destination, source in local.on_demand_sources : {
    path = destination, permissions = "0600", owner = "root:root", encoding = "b64", content = filebase64(source)
    }], [{
    path = "/etc/orderflow-demo.json", permissions = "0600", owner = "root:root", encoding = "b64",
    content = base64encode(jsonencode({ account = var.expected_account_id, region = var.aws_region,
    project = var.project_name, environment = var.environment, maxRuntimeHours = var.demo_max_runtime_hours }))
  }]) : []
}
resource "aws_iam_role_policy" "on_demand_read" {
  count = var.enable_on_demand ? 1 : 0
  name  = "verify-dedicated-host-stop-safety"
  role  = aws_iam_role.application.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect    = "Allow", Action = ["ec2:DescribeInstances", "ec2:DescribeInstanceAttribute"], Resource = ["*"]
    Condition = { StringEquals = { "aws:RequestedRegion" = var.aws_region } }
  }] }) # EC2 Describe APIs do not support resource-level restrictions. No write permission.
}
output "on_demand_runtime" {
  value = { enabled = var.enable_on_demand, max_hours = var.demo_max_runtime_hours, shutdown_behavior = "stop" }
}
