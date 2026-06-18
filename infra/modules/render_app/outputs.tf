output "service_id" {
  description = "The ID of the created Render web service"
  value       = render_web_service.app.id
}

output "service_url" {
  description = "The URL of the created Render web service"
  value       = render_web_service.app.url
}
