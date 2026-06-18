resource "render_web_service" "app" {
  name   = var.app_name
  plan   = var.plan
  region = var.region

  runtime_source = {
    image = {
      # TODO: replace with your real published image, e.g. "ghcr.io/bicosteve/rapid-engine:latest"
      image_url = var.docker_image_url
    }
  }
}
