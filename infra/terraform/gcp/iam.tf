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

# Vertex AI 서비스 에이전트가 GCS 버킷의 객체를 읽을 수 있도록 권한 부여.
# Gemini가 GCS URI(fileData)로 PDF를 참조할 때, 실제 객체 fetch는
# 'service-<PROJECT_NUMBER>@gcp-sa-aiplatform.iam.gserviceaccount.com'이 수행한다.
data "google_project" "this" {
  project_id = var.project_id
}

resource "google_storage_bucket_iam_member" "vertex_agent_read" {
  bucket = google_storage_bucket.ai_files.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:service-${data.google_project.this.number}@gcp-sa-aiplatform.iam.gserviceaccount.com"

  depends_on = [google_project_service.aiplatform]
}
