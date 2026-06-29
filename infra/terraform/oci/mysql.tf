# ============================================================
# 운영 MySQL HeatWave 매니지드 자동 백업 + PITR 활성화 (T2)
# ============================================================
# FR-001: 매니지드 자동 백업 활성화, 보존 ≥ 7일, PITR 지원
# FR-012: 보존 기간이 무료 할당량을 초과하지 않도록 산정
#         (T1 측정: 운영 DB 194 MiB, 무압축 최악 가정으로도 안전권)
#
# DB System 자체는 Terraform이 관리하지 않으며(콘솔 생성 자산),
# 본 모듈은 backup_policy 속성만 OCI CLI를 통해 idempotent하게 갱신한다.
#
# 사전 요구: oci CLI 설치, ~/.oci/config DEFAULT 프로필 인증.

variable "mysql_db_system_id" {
  type        = string
  description = "운영 MySQL HeatWave DB System OCID. terraform.tfvars 또는 TF_VAR_mysql_db_system_id 환경변수로 주입."
}

variable "mysql_backup_retention_days" {
  type        = number
  default     = 7
  description = "매니지드 자동 백업 보존 기간(일). T1 산정 기준 7일."

  validation {
    condition     = var.mysql_backup_retention_days >= 1 && var.mysql_backup_retention_days <= 35
    error_message = "OCI MySQL 백업 보존 기간은 1~35일 범위여야 합니다."
  }
}

# 현재 DB System 상태 참조 (검증용; DB System 자체는 콘솔에서 관리)
data "oci_mysql_mysql_db_system" "qasker_prod" {
  db_system_id = var.mysql_db_system_id
}

# backup_policy 갱신: triggers 변경 시 재실행되어 idempotent하게 동작.
resource "null_resource" "mysql_backup_policy" {
  triggers = {
    db_system_id   = var.mysql_db_system_id
    retention_days = var.mysql_backup_retention_days
    pitr_enabled   = "true"
  }

  provisioner "local-exec" {
    interpreter = ["/bin/bash", "-c"]
    command     = <<-EOT
      set -euo pipefail
      POLICY_FILE=$(mktemp)
      trap 'rm -f "$POLICY_FILE"' EXIT
      cat > "$POLICY_FILE" <<JSON
      {
        "isEnabled": true,
        "retentionInDays": ${var.mysql_backup_retention_days},
        "pitrPolicy": {
          "isEnabled": true
        }
      }
      JSON
      oci mysql db-system update \
        --db-system-id "${var.mysql_db_system_id}" \
        --backup-policy "file://$POLICY_FILE" \
        --force \
        --wait-for-state ACTIVE
    EOT
  }
}

# T2 검증: apply 후 24h 경과 시점에 실행하여 자동 백업 ≥ 1건 확인 (US1 Acceptance 1).
output "mysql_backup_verify_command" {
  description = "24h 후 매니지드 자동 백업 목록 조회 명령"
  value       = "oci mysql backup list --db-system-id ${var.mysql_db_system_id} --creation-type AUTOMATIC --sort-by TIME_CREATED --sort-order DESC --output table"
}

# 현재 적용된 백업 정책 확인 (null_resource apply 직후 refresh 필요)
output "mysql_current_backup_policy" {
  description = "현재 DB System의 backup_policy 상태"
  value       = data.oci_mysql_mysql_db_system.qasker_prod.backup_policy
}
