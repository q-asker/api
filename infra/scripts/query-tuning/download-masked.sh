#!/usr/bin/env bash
# =============================================================
# download-masked.sh — OCI 마스킹본을 로컬 파일로 내려받고 sha256 검증. 성공 시 파일 경로를 stdout 출력.
#   책임: 마스킹본 "다운로드"(OCI 읽기)만. 복원은 restore-x1.sh 가 한다.
#   masked 전용 — 원본(DR)은 트러스트 존 백업(plg) 소관이라 받지 않는다.
# 사용: download-masked.sh [--latest | <masked-object-key>] [--out=<file>] [--no-verify]
#   예) f=$(download-masked.sh --latest); restore-x1.sh --file="$f"
# 환경: BUCKET(qasker-mysql-backup) OCI_PROFILE(BACKUP_READER) MASKED_PREFIX(masked/)
# =============================================================
set -uo pipefail
: "${BUCKET:=qasker-mysql-backup}"; : "${OCI_PROFILE:=BACKUP_READER}"; : "${MASKED_PREFIX:=masked/}"
OBJECT_KEY=""; OUT=""; NO_VERIFY=0
sha256(){ if command -v sha256sum >/dev/null 2>&1; then sha256sum "$@"; else shasum -a 256 "$@"; fi; }
log(){ echo "[download-masked] $*" >&2; }   # 로그는 stderr — stdout 은 파일 경로 전용(파이프 위임용)

while (( $# > 0 )); do case "$1" in
  --latest)    OBJECT_KEY="__LATEST__" ;;
  --out=*)     OUT="${1#--out=}" ;;
  --no-verify) NO_VERIFY=1 ;;
  -h|--help)   grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  --*)         log "알 수 없는 옵션: $1"; exit 1 ;;
  *)           OBJECT_KEY="$1" ;;
esac; shift; done
[[ -n "$OBJECT_KEY" ]] || { log "사용: download-masked.sh [--latest | <masked-object-key>] [--out=file]"; exit 1; }
[[ "$OBJECT_KEY" != "__LATEST__" && "$OBJECT_KEY" != "$MASKED_PREFIX"* ]] && { log "masked 전용 — 객체키가 ${MASKED_PREFIX} 로 시작해야 함: $OBJECT_KEY"; exit 1; }
command -v oci >/dev/null || { log "oci CLI 필요"; exit 1; }

if [[ "$OBJECT_KEY" == "__LATEST__" ]]; then
  log "--latest: masked/ 최신 sql.gz 조회..."
  OBJECT_KEY="$(oci --profile "$OCI_PROFILE" os object list -bn "$BUCKET" --prefix "$MASKED_PREFIX" --all \
    --query 'sort_by(data,&name)[?ends_with(name,`sql.gz`)]|[-1].name' --raw-output 2>/dev/null)"
  [[ -z "$OBJECT_KEY" || "$OBJECT_KEY" == "null" ]] && { log "마스킹 백업 없음"; exit 1; }
  log "선택: $OBJECT_KEY"
fi

OUT="${OUT:-/tmp/$(basename "$OBJECT_KEY")}"
oci --profile "$OCI_PROFILE" os object get -bn "$BUCKET" --name "$OBJECT_KEY" --file "$OUT" >/dev/null 2>&1 \
  || { log "다운로드 실패: $OBJECT_KEY"; exit 1; }

if (( ! NO_VERIFY )); then
  SHA="$(mktemp)"
  if oci --profile "$OCI_PROFILE" os object get -bn "$BUCKET" --name "${OBJECT_KEY%.sql.gz}.sha256" --file "$SHA" >/dev/null 2>&1; then
    [[ "$(awk '{print $1}' "$SHA")" == "$(sha256 "$OUT" | awk '{print $1}')" ]] || { log "sha256 불일치"; rm -f "$SHA"; exit 1; }
    log "sha256 OK"
  else
    log "sha 없음 — 검증 건너뜀"
  fi
  rm -f "$SHA"
fi

log "완료: $OUT"
echo "$OUT"   # stdout: 다운로드된 파일 경로 (restore-x1 이 소비)
