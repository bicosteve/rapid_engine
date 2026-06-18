terraform {
  required_providers {
    render = {
      source  = "render-oss/render"
      version = ">= 1.8.0"
    }
  }
}

provider "render" {
  api_key = var.render_api_key
}
