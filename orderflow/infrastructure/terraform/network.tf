resource "aws_vpc" "application" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = local.name }
  depends_on           = [terraform_data.deployment_guard]
}
resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.application.id
  cidr_block              = var.subnet_cidr
  availability_zone       = local.availability_zone
  map_public_ip_on_launch = false
  tags                    = { Name = "${local.name}-public" }
}
resource "aws_internet_gateway" "application" {
  vpc_id = aws_vpc.application.id
  tags   = { Name = local.name }
}
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.application.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.application.id
  }
  tags = { Name = "${local.name}-public" }
}
resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}
