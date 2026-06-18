variable "render_api_key" {
  description = "Render API Key"
  type        = string
  sensitive   = true
}


variable "owner_id" {
  description = "Owner of the render account"
  type        = string
  sensitive   = true
}

variable "workspace_id" {
  description = "Workspace id key"
  type        = string
  sensitive   = true
}

variable "region" {
  description = "Region where the project is located"
  type        = string
  sensitive   = false
}

variable "app_name" {
  description = "Applications name"
  type        = string
  sensitive   = false
}

variable "project_id" {
  description = "Id of the project"
  type        = string
  sensitive   = false
}

variable "docker_image_url" {
  description = "URL of the Docker image to deploy (e.g. ghcr.io/bicosteve/rapid-engine:latest)"
  type        = string
}
