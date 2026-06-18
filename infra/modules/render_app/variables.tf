variable "app_name" {
  description = "The running app name"
  type        = string
}

variable "region" {
  description = "Region where the service is deployed (e.g. frankfurt, oregon, ohio, singapore)"
  type        = string
}

variable "plan" {
  description = "Render service plan (e.g. starter, standard, pro)"
  type        = string
  default     = "starter"
}

variable "docker_image_url" {
  description = "URL of the Docker image to deploy (e.g. ghcr.io/bicosteve/rapid-engine:latest)"
  type        = string
}
