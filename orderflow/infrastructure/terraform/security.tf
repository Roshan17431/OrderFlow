resource "aws_security_group" "application" {
  name_prefix = "${local.name}-"
  description = "Caddy web ingress only; administration through outbound SSM channels"
  vpc_id      = aws_vpc.application.id
  tags        = { Name = local.name }
}
resource "aws_vpc_security_group_ingress_rule" "web" {
  for_each          = toset(["80", "443"])
  security_group_id = aws_security_group.application.id
  description       = each.key == "80" ? "HTTPS redirect and ACME HTTP challenge" : "Public HTTPS to Caddy"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = tonumber(each.key)
  to_port           = tonumber(each.key)
  ip_protocol       = "tcp"
}
resource "aws_vpc_security_group_egress_rule" "web" {
  for_each          = toset(["80", "443"])
  security_group_id = aws_security_group.application.id
  description       = "Outbound package updates, ECR, SSM, S3 and certificate renewal"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = tonumber(each.key)
  to_port           = tonumber(each.key)
  ip_protocol       = "tcp"
}
resource "aws_vpc_security_group_egress_rule" "dns" {
  for_each          = toset(["tcp", "udp"])
  security_group_id = aws_security_group.application.id
  description       = "VPC DNS only"
  cidr_ipv4         = var.vpc_cidr
  from_port         = 53
  to_port           = 53
  ip_protocol       = each.key
}
