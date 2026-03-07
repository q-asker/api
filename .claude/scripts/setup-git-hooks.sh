#!/bin/sh
# JIRA 티켓 접두사 Git Hook 설정 스크립트
# 사용법: ./scripts/setup-git-hooks.sh [--auto]
#   --auto: 대화형 확인 없이 자동 설치 (CI/빌드 도구 통합용)

set -e

HOOKS_DIR=".githooks"
HOOK_FILE="$HOOKS_DIR/prepare-commit-msg"
AUTO_MODE=false

[ "$1" = "--auto" ] && AUTO_MODE=true

# 1) 브랜치에서 JIRA 티켓 추출
BRANCH=$(git symbolic-ref --short HEAD 2>/dev/null || echo "")
TICKET=$(echo "$BRANCH" | grep -oE '[A-Z]+-[0-9]+' | head -1)

if [ -z "$TICKET" ]; then
  echo "JIRA 티켓을 감지하지 못했습니다. (현재 브랜치: $BRANCH)"
  exit 0
fi

# 2) 사용자 확인 (--auto가 아닐 때)
if [ "$AUTO_MODE" = false ]; then
  printf "JIRA 티켓 [%s] 감지. 커밋 접두사 훅을 설치할까요? (y/n) " "$TICKET"
  read -r REPLY
  case "$REPLY" in
    [yY]*) ;;
    *) echo "건너뜁니다."; exit 0 ;;
  esac
fi

# 3) 훅 스크립트 생성
if [ -f "$HOOK_FILE" ]; then
  echo "이미 $HOOK_FILE 이 존재합니다. 건너뜁니다."
else
  mkdir -p "$HOOKS_DIR"
  cat > "$HOOK_FILE" << 'HOOK'
#!/bin/sh
# JIRA 티켓 접두사 자동 추가
BRANCH_NAME=$(git symbolic-ref --short HEAD 2>/dev/null)
TICKET=$(echo "$BRANCH_NAME" | grep -oE '[A-Z]+-[0-9]+' | head -1)

if [ -n "$TICKET" ]; then
  COMMIT_MSG=$(cat "$1")
  if ! echo "$COMMIT_MSG" | grep -qE "^\[$TICKET\]"; then
    echo "[$TICKET] $COMMIT_MSG" > "$1"
  fi
fi
HOOK
  chmod +x "$HOOK_FILE"
  echo "훅 생성 완료: $HOOK_FILE"
fi

# 4) core.hooksPath 설정
git config core.hooksPath "$HOOKS_DIR"
echo "git config core.hooksPath -> $HOOKS_DIR"

echo "설정 완료. 커밋 시 [$TICKET] 접두사가 자동 추가됩니다."
