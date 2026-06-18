module "rapid-engine" {
  source = "./modules/render_app"

  app_name         = var.app_name
  region           = var.region
  docker_image_url = var.docker_image_url
  env_vars         = var.env_vars
  secret_env_vars  = var.secret_env_vars
}

