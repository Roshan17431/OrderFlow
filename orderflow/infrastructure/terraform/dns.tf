resource "aws_eip" "application" {
  count      = var.enable_elastic_ip ? 1 : 0
  domain     = "vpc"
  tags       = { Name = local.name }
  depends_on = [terraform_data.deployment_guard, aws_internet_gateway.application]
}
resource "aws_eip_association" "application" {
  count         = var.enable_elastic_ip ? 1 : 0
  instance_id   = aws_instance.application.id
  allocation_id = aws_eip.application[0].id
}
resource "aws_route53_record" "application" {
  count           = var.enable_route53 && var.enable_elastic_ip ? 1 : 0
  zone_id         = var.route53_zone_id
  name            = var.domain_name
  type            = "A"
  ttl             = 300
  records         = [aws_eip.application[0].public_ip]
  allow_overwrite = false
  depends_on      = [terraform_data.deployment_guard]
}
