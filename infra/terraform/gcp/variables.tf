variable "project_id" {
  description = "GCP 프로젝트 ID"
  type        = string
  default     = "project-e9d67c94-3157-456d-83b"
}

variable "region" {
  description = "GCP 리전"
  type        = string
  default     = "asia-northeast3"
}

variable "gcs_bucket_name" {
  description = "Vertex AI 컨텍스트 캐싱용 PDF 임시 저장 버킷"
  type        = string
  default     = "q-asker-ai-files"
}

variable "service_account_id" {
  description = "AI 서비스 계정 ID"
  type        = string
  default     = "q-asker-ai"
}
