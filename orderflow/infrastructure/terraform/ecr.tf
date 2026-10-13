resource "aws_ecr_repository" "application" {
  for_each             = local.services
  name                 = "${var.project_name}/${each.key}"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = false
  image_scanning_configuration {
    scan_on_push = true
  }
  encryption_configuration {
    encryption_type = "AES256"
  }
  depends_on = [terraform_data.deployment_guard]
}
resource "aws_ecr_lifecycle_policy" "application" {
  for_each   = aws_ecr_repository.application
  repository = each.value.name
  # Tagged Git SHA/semantic release images are never expired automatically.
  policy = jsonencode({ rules = [{
    rulePriority = 1
    description  = "Expire only untagged build debris after 14 days; retain release tags"
    selection = {
      tagStatus   = "untagged"
      countType   = "sinceImagePushed"
      countUnit   = "days"
      countNumber = 14
    }
    action = { type = "expire" }
  }] })
}
