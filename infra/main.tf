module "rapid-engine" {
  source = "./modules/render_app"

  app_name         = var.app_name
  region           = var.region
  docker_image_url = var.docker_image_url
}
