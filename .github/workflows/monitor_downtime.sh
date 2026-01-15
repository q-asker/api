#!/bin/bash

WEBHOOK="$1"
TARGET="http://localhost:8080/actuator/health" # 내부 호출이므로 localhost 고정
TIMEOUT=180
HOST_NAME=$(hostname)

# Slack 전송 함수
function send_slack() {
  local MESSAGE="$1"

  # 1. 봇 이름 및 아이콘 설정
  local USERNAME="배포 상태 알림이"
  local ICON=":rocket:" # 배포=:rocket:, 알림=:loudspeaker:, 경고=:rotating_light:

  # 2. 메시지 내용에 따라 아이콘 동적 변경 (선택 사항)
  # 실패/Time out 문구가 있으면 경광등(:rotating_light:)으로 변경
  if [[ "$MESSAGE" == *"실패"* ]] || [[ "$MESSAGE" == *"Time out"* ]]; then
      ICON=":rotating_light:"
  elif [[ "$MESSAGE" == *"완료"* ]]; then
      ICON=":white_check_mark:"
  fi

  local PAYLOAD=$(cat <<EOF
{
  "username": "$USERNAME",
  "icon_emoji": "$ICON",
  "text": "$MESSAGE"
}
EOF
)
  curl -s -X POST -H 'Content-type: application/json' --data "$PAYLOAD" "$WEBHOOK" > /dev/null
}

# 1. 초기 상태 확인
# -s: Silent, -o /dev/null: 출력 버림, -w: HTTP 코드만 출력
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$TARGET")

# [수정됨] 배포 전인데 이미 서버가 죽어있다면 알림을 보내고 종료
if [ "$STATUS" -ne 200 ]; then
  send_slack "⚠️ *모니터링 건너뜀*\n- 서버: ${HOST_NAME}\n- 원인: 배포 전 이미 비정상 상태 (Status: ${STATUS})"
  exit 0
fi

send_slack "🚀 *배포 모니터링 시작*\n- 서버: ${HOST_NAME}\n- 상태: 다운타임 측정 대기 중"

START_WAIT=$(date +%s)
DOWNTIME_START=0
DOWNTIME_END=0

# 2. 다운타임 시작 감지 (서버가 죽을 때까지)
while true; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 1 "$TARGET")
  if [ "$CODE" -ne 200 ]; then
    DOWNTIME_START=$(date +%s)
    send_slack "🚀 *배포 모니터링 시작*\n- 서버: ${HOST_NAME}\n- 상태: 다운타임 측정 시작"
    break
  fi
  # 타임아웃 체크
  if [ $(( $(date +%s) - START_WAIT )) -gt $TIMEOUT ]; then
    send_slack "❌ *배포 실패 (Time out)*\n- 서버: ${HOST_NAME}\n- 원인: 기존 서버가 중단되지 않음"
    exit 1
  fi
  sleep 0.5
done

# 3. 서비스 복구 감지 (서버가 살 때까지)
while true; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 1 "$TARGET")
  if [ "$CODE" -eq 200 ]; then
    DOWNTIME_END=$(date +%s)
    break
  fi
  if [ $(( $(date +%s) - DOWNTIME_START )) -gt $TIMEOUT ]; then
    send_slack "❌ *배포 실패 (Time out)*\n- 서버: ${HOST_NAME}\n- 원인: 신규 서버가 구동되지 않음"
    exit 1
  fi
  sleep 1
done

DURATION=$((DOWNTIME_END - DOWNTIME_START))

# 4. 완료 Slack 전송
send_slack "✅ *배포 완료 및 서비스 정상화*\n- 서버: ${HOST_NAME}\n- 다운타임: *${DURATION}초*"