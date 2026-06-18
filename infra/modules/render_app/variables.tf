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
  description = "URL of the Docker image to deploy (e.g. docker.io/bixoloo/rapid-engine:latest)"
  type        = string
}

variable "env_vars" {
  description = "Map of plain (non-secret) environment variables injected into the running container."
  type        = map(string)
  default     = {}
}

variable "secret_env_vars" {
  description = "Map of sensitive environment variables (API keys, passwords) injected into the running container."
  type        = map(string)
  default     = {}
  sensitive   = true
}

