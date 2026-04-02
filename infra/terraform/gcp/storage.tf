# Vertex AI 컨텍스트 캐싱용 PDF 임시 저장 버킷
resource "google_storage_bucket" "ai_files" {
  name     = var.gcs_bucket_name
  location = var.region

  storage_class               = "STANDARD"
  uniform_bucket_level_access = true

  # 1일 후 자동 삭제 (GCS 최소 단위가 일 단위)
  # 애플리케이션에서 캐시 삭제 시 즉시 삭제도 병행
  lifecycle_rule {
    condition {
      age = 1
    }
    action {
      type = "Delete"
    }
  }

  depends_on = [google_project_service.storage]
}
