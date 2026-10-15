resource "aws_cloudwatch_log_group" "host" {
  count             = var.enable_observability ? 1 : 0
  name              = "/${var.project_name}/${var.environment}/host"
  retention_in_days = var.cloudwatch_log_retention_days
  depends_on        = [terraform_data.deployment_guard]
}
resource "aws_iam_role_policy" "monitoring" {
  count = var.enable_observability ? 1 : 0
  name  = "bounded-host-monitoring"
  role  = aws_iam_role.application.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    {
      Effect   = "Allow"
      Action   = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"]
      Resource = ["${aws_cloudwatch_log_group.host[0].arn}:*"]
    },
    {
      Effect    = "Allow"
      Action    = ["cloudwatch:PutMetricData"]
      Resource  = ["*"]
      Condition = { StringEquals = { "cloudwatch:namespace" = "OrderFlow/${var.environment}" } }
    }
  ] })
}
resource "aws_sns_topic" "alarms" {
  count      = var.enable_observability && var.alarm_notification_email != "" ? 1 : 0
  name       = "${local.name}-alarms"
  depends_on = [terraform_data.deployment_guard]
}
resource "aws_sns_topic_subscription" "email" {
  count     = var.enable_observability && var.alarm_notification_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.alarms[0].arn
  protocol  = "email"
  endpoint  = var.alarm_notification_email
}
resource "aws_cloudwatch_metric_alarm" "cpu" {
  count               = var.enable_observability ? 1 : 0
  alarm_name          = "${local.name}-cpu"
  alarm_description   = "Sustained CPU load; examine throttling and service latency before resizing"
  namespace           = "AWS/EC2"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  dimensions          = { InstanceId = aws_instance.application.id }
  treat_missing_data  = "missing"
  alarm_actions       = var.alarm_notification_email != "" ? [aws_sns_topic.alarms[0].arn] : []
  ok_actions          = var.alarm_notification_email != "" ? [aws_sns_topic.alarms[0].arn] : []
}
resource "aws_cloudwatch_metric_alarm" "status" {
  count               = var.enable_observability ? 1 : 0
  alarm_name          = "${local.name}-status"
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  dimensions          = { InstanceId = aws_instance.application.id }
  treat_missing_data  = "breaching"
  alarm_actions       = var.alarm_notification_email != "" ? [aws_sns_topic.alarms[0].arn] : []
  ok_actions          = var.alarm_notification_email != "" ? [aws_sns_topic.alarms[0].arn] : []
}
resource "aws_budgets_budget" "monthly" {
  count        = var.enable_budget_alerts ? 1 : 0
  name         = "${local.name}-account-monthly-cost"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"
  # Account-wide deliberately: tag-filtered budgets can miss unallocated costs.
  dynamic "notification" {
    for_each = length(var.budget_actual_thresholds_usd) == 0 ? { default = 80 } : { for amount in var.budget_actual_thresholds_usd : tostring(amount) => amount }
    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = length(var.budget_actual_thresholds_usd) == 0 ? "PERCENTAGE" : "ABSOLUTE_VALUE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = [var.budget_notification_email]
    }
  }
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.budget_notification_email]
  }
  depends_on = [terraform_data.deployment_guard]
}
resource "aws_sns_topic_policy" "alarms" {
  count = var.enable_observability && var.alarm_notification_email != "" ? 1 : 0
  arn   = aws_sns_topic.alarms[0].arn
  policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Sid       = "ThisDeploymentsCloudWatchAlarmsOnly"
    Effect    = "Allow"
    Principal = { Service = "cloudwatch.amazonaws.com" }
    Action    = "sns:Publish"
    Resource  = aws_sns_topic.alarms[0].arn
    Condition = {
      StringEquals = { "aws:SourceAccount" = local.account_id }
      ArnEquals    = { "aws:SourceArn" = [aws_cloudwatch_metric_alarm.cpu[0].arn, aws_cloudwatch_metric_alarm.status[0].arn] }
    }
  }] })
}
