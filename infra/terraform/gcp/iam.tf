# AI 전용 서비스 계정
resource "google_service_account" "ai" {
  account_id   = var.service_account_id
  display_name = "Q-Asker AI Service"
}

# GCS 파일 업로드/삭제 권한
resource "google_project_iam_member" "ai_storage" {
  project = var.project_id
  role    = "roles/storage.objectAdmin"
  member  = "serviceAccount:${google_service_account.ai.email}"
}

# Vertex AI 호출 권한
resource "google_project_iam_member" "ai_vertex" {
  project = var.project_id
  role    = "roles/aiplatform.user"
  member  = "serviceAccount:${google_service_account.ai.email}"
}
