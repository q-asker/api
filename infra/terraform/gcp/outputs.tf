output "gcs_bucket_name" {
  description = "GCS 버킷 이름"
  value       = google_storage_bucket.ai_files.name
}

output "service_account_email" {
  description = "AI 서비스 계정 이메일"
  value       = google_service_account.ai.email
}

output "project_id" {
  description = "GCP 프로젝트 ID"
  value       = var.project_id
}

output "region" {
  description = "GCP 리전"
  value       = var.region
}