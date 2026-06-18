output "service_id" {
  description = "The ID of the deployed Render service"
  value       = module.rapid-engine.service_id
}

output "service_url" {
  description = "The URL of the deployed Render service"
  value       = module.rapid-engine.service_url
}
