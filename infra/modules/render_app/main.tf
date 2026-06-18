# Merge plain + secret vars into a single map of { key = { value = "..." } }
# The Render provider marks every env var value as sensitive internally.
locals {
  all_env_vars = merge(var.env_vars, var.secret_env_vars)

  render_env_vars = {
    for key, value in local.all_env_vars :
    key => { value = value }
  }
}

resource "render_web_service" "app" {
  name   = var.app_name
  plan   = var.plan
  region = var.region

  runtime_source = {
    image = {
      image_url = var.docker_image_url
    }
  }

  env_vars = local.render_env_vars
}


