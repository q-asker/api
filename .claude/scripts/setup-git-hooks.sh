#!/bin/sh
# Git Hook 설정 스크립트 (init-context.json 기반 동적 생성)
# 사용법: ./scripts/setup-git-hooks.sh [--auto]
#   --auto: 대화형 확인 없이 자동 설치 (CI/빌드 도구 통합용)

set -e

HOOKS_DIR=".githooks"
HOOK_FILE="$HOOKS_DIR/prepare-commit-msg"
PRE_COMMIT_FILE="$HOOKS_DIR/pre-commit"
CONTEXT_FILE=".claude/init-context.json"
AUTO_MODE=false

[ "$1" = "--auto" ] && AUTO_MODE=true

# init-context.json에서 포맷 명령어 읽기
FORMAT_CHECK_CMD=""
FORMAT_APPLY_CMD=""
FORMATTER=""
if [ -f "$CONTEXT_FILE" ]; then
  FORMATTER=$(grep -o '"formatter"[[:space:]]*:[[:space:]]*"[^"]*"' "$CONTEXT_FILE" | sed 's/.*"formatter"[[:space:]]*:[[:space:]]*"\([^"]*\)"/\1/' || echo "")
  FORMAT_CHECK_CMD=$(grep -o '"formatCommand"[[:space:]]*:[[:space:]]*"[^"]*"' "$CONTEXT_FILE" | sed 's/.*"formatCommand"[[:space:]]*:[[:space:]]*"\([^"]*\)"/\1/' || echo "")

  # formatCommand에서 Apply → Check 변환 (Gradle 기반)
  if echo "$FORMAT_CHECK_CMD" | grep -q "Apply"; then
    FORMAT_APPLY_CMD="$FORMAT_CHECK_CMD"
    FORMAT_CHECK_CMD=$(echo "$FORMAT_CHECK_CMD" | sed 's/Apply/Check/g')
  elif [ -n "$FORMAT_CHECK_CMD" ]; then
    FORMAT_APPLY_CMD=$(echo "$FORMAT_CHECK_CMD" | sed 's/Check/Apply/g')
  fi
fi

# --- 1) prepare-commit-msg 훅 ---

BRANCH=$(git symbolic-ref --short HEAD 2>/dev/null || echo "")
TICKET=$(echo "$BRANCH" | grep -oE '[A-Z]+-[0-9]+' | head -1)

if [ -z "$TICKET" ]; then
  echo "JIRA 티켓을 감지하지 못했습니다. (현재 브랜치: $BRANCH)"
  echo "prepare-commit-msg 훅은 건너뜁니다."
else
  # 사용자 확인 (--auto가 아닐 때)
  INSTALL_PREPARE=true
  if [ "$AUTO_MODE" = false ]; then
    printf "JIRA 티켓 [%s] 감지. 커밋 접두사 훅을 설치할까요? (y/n) " "$TICKET"
    read -r REPLY
    case "$REPLY" in
      [yY]*) ;;
      *) echo "건너뜁니다."; INSTALL_PREPARE=false ;;
    esac
  fi

  if [ "$INSTALL_PREPARE" = true ]; then
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
fi

# --- 2) pre-commit 훅 (init-context.json의 formatCommand 기반) ---

if [ -z "$FORMAT_CHECK_CMD" ]; then
  echo "포맷터가 감지되지 않았습니다. pre-commit 훅은 건너뜁니다."
else
  INSTALL_PRECOMMIT=true
  if [ "$AUTO_MODE" = false ]; then
    printf "포맷터 [%s] 감지. pre-commit 훅을 설치할까요? (y/n) " "$FORMATTER"
    read -r REPLY
    case "$REPLY" in
      [yY]*) ;;
      *) echo "건너뜁니다."; INSTALL_PRECOMMIT=false ;;
    esac
  fi

  if [ "$INSTALL_PRECOMMIT" = true ]; then
    mkdir -p "$HOOKS_DIR"
    # 기존 훅이 있어도 사양이 다르면 덮어쓰기
    cat > "$PRE_COMMIT_FILE" << HOOK
#!/bin/sh
# 포맷 검증 ($FORMATTER) — 위반 시 커밋 차단
echo "포맷 검증 중 ($FORMAT_CHECK_CMD)..."
$FORMAT_CHECK_CMD -q 2>/dev/null
if [ \$? -ne 0 ]; then
  echo ""
  echo "포맷 위반이 발견되었습니다."
  echo "$FORMAT_APPLY_CMD 를 실행한 뒤 다시 커밋하세요."
  exit 1
fi
HOOK
    chmod +x "$PRE_COMMIT_FILE"
    echo "훅 생성 완료: $PRE_COMMIT_FILE (포맷터: $FORMATTER, 명령어: $FORMAT_CHECK_CMD)"
  fi
fi

# --- 3) core.hooksPath 설정 ---

if [ -d "$HOOKS_DIR" ]; then
  git config core.hooksPath "$HOOKS_DIR"
  echo "git config core.hooksPath -> $HOOKS_DIR"
  echo "설정 완료."
else
  echo "설치된 훅이 없습니다. core.hooksPath 설정을 건너뜁니다."
fi
