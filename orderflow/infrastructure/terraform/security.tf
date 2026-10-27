resource "aws_security_group" "application" {
  name_prefix = "${local.name}-"
  description = "Caddy web ingress only; administration through outbound SSM channels"
  vpc_id      = aws_vpc.application.id
  tags        = { Name = local.name }
}
resource "aws_vpc_security_group_ingress_rule" "web" {
  for_each = merge({ "80" = { port = 80, cidr = "0.0.0.0/0" } }, {
    for cidr in toset(var.https_ingress_cidrs) : (cidr == "0.0.0.0/0" ? "443" : "443:${cidr}") => { port = 443, cidr = cidr }
  })
  security_group_id = aws_security_group.application.id
  description       = each.value.port == 80 ? "HTTPS redirect and ACME HTTP challenge" : "Approved HTTPS clients to Caddy"
  cidr_ipv4         = each.value.cidr
  from_port         = each.value.port
  to_port           = each.value.port
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
