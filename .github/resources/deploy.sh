#!/bin/bash

# Slack 전송 함수
WEBHOOK="${SLACK_WEBHOOK_URL:?SLACK_WEBHOOK_URL is required}"

function send_slack() {
  local MESSAGE="$1"
  local USERNAME="배포 상태 알림이"
  local ICON=":gear:"

  if [[ "$MESSAGE" == *"실패"* ]] || [[ "$MESSAGE" == *"초과"* ]]; then
      ICON=":rotating_light:"
  elif [[ "$MESSAGE" == *"완료"* ]] || [[ "$MESSAGE" == *"성공"* ]]; then
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
  curl --connect-timeout 3 --max-time 5 -s -X POST -H 'Content-type: application/json' --data "$PAYLOAD" "$WEBHOOK" > /dev/null
}

# ==============================================================================
# 설정 변수
# ==============================================================================
MAX_RETRIES=36
SLEEP_TIME=5
SHUTDOWN_TIMEOUT=60
PULL_TIMEOUT=120
BLUE_PORT="${1:?BLUE_PORT is required}"
GREEN_PORT="${2:?GREEN_PORT is required}"
TOTAL_START_TIME=$(date +%s)

send_slack "🚀 블루-그린 배포 시작..."

# 1. 현재 구동 중인 프로필 확인
CURRENT_PROFILE=$(curl -s --connect-timeout 3 --max-time 5 http://localhost:8080/status | grep -o '"profile":"[^"]*"' | cut -d'"' -f4)

# 2. 타겟 프로필 및 포트 설정
if [[ "$CURRENT_PROFILE" == *"blue"* ]]; then
  CURRENT_CONTAINER="app-blue"
  TARGET_PORT=$GREEN_PORT
  TARGET_CONTAINER="app-green"
else
  CURRENT_CONTAINER="app-green"
  TARGET_PORT=$BLUE_PORT
  TARGET_CONTAINER="app-blue"
fi

OLD_IMAGE_NAME=$(docker inspect --format='{{.Config.Image}}' "$CURRENT_CONTAINER" 2>/dev/null || echo "(기존 이미지 없음)")
NEW_IMAGE_NAME="${3:?NEW_IMAGE_NAME is required}"

send_slack ">>> 현재 컨테이너 : $CURRENT_CONTAINER ($OLD_IMAGE_NAME)"
send_slack ">>> 띄울 컨테이너 : $TARGET_CONTAINER ($NEW_IMAGE_NAME)"

# 3. 최신 이미지 Pull 및 컨테이너 실행
send_slack ">>> Docker Pull 시작 ($NEW_IMAGE_NAME)..."

timeout $PULL_TIMEOUT docker compose pull $TARGET_CONTAINER
EXIT_CODE=$?

if [ $EXIT_CODE -eq 124 ]; then
    send_slack ">>> ❌ Docker Pull 시간 초과! (${PULL_TIMEOUT}초 경과)"
    exit 1
elif [ $EXIT_CODE -ne 0 ]; then
    send_slack ">>> ❌ Docker Pull 실패! (Exit Code: $EXIT_CODE)"
    exit 1
fi

send_slack ">>> Docker Pull 완료. 컨테이너 시작 중..."
docker compose up -d $TARGET_CONTAINER

# 4. 헬스 체크
send_slack ">>> 헬스 체크 시작 (Port: $TARGET_PORT)..."
HEALTH_START_TIME=$(date +%s)

for ((i=1; i<=MAX_RETRIES; i++)); do
  RESPONSE=$(curl -s --connect-timeout 3 --max-time 5 http://localhost:$TARGET_PORT/status)
  UP_CHECK=$(echo "$RESPONSE" | grep -o '"status":"UP"')

  if [ ! -z "$UP_CHECK" ]; then
    HEALTH_END_TIME=$(date +%s)
    HEALTH_DURATION=$((HEALTH_END_TIME - HEALTH_START_TIME))
    send_slack ">>> ✅ 헬스 체크 성공! (구동 시간: ${HEALTH_DURATION}초)"
    break
  fi

  if [ $i -eq $MAX_RETRIES ]; then
    echo ">>> ❌ Health Check Failed after $MAX_RETRIES attempts."
    send_slack ">>> ⚠️ 배포 실패!! 컨테이너($TARGET_CONTAINER) 중지... \n응답값: $RESPONSE"
    docker compose stop $TARGET_CONTAINER
    exit 1
  fi

  send_slack ">>> ($TARGET_CONTAINER) 응답 대기중... ($i/$MAX_RETRIES)"
  sleep $SLEEP_TIME
done

# 5. Nginx 트래픽 전환
send_slack ">>> Nginx 트래픽 전환 ($TARGET_CONTAINER)..."
echo "set \$service_url http://$TARGET_CONTAINER:8080;" > ./nginx/conf.d/service-url.inc

IS_NGINX_RUNNING=$(docker ps | grep nginx)

if [ -z "$IS_NGINX_RUNNING" ]; then
    send_slack ">>> Nginx가 실행 중이지 않습니다. Nginx 시작..."
    docker compose up -d nginx
else
    send_slack ">>> Nginx가 실행 중입니다. 설정 리로드(Reload)..."
    docker exec nginx nginx -s reload
fi

# 6. 이전 버전 컨테이너 중지
if [ -n "$CURRENT_PROFILE" ]; then
  send_slack ">>> 구 버전 컨테이너($CURRENT_CONTAINER) 중지 (대기 시간: ${SHUTDOWN_TIMEOUT}초)..."

  STOP_START=$(date +%s)
  docker compose stop -t $SHUTDOWN_TIMEOUT $CURRENT_CONTAINER
  STOP_END=$(date +%s)
  STOP_DURATION=$((STOP_END - STOP_START))

  if [ $STOP_DURATION -ge $SHUTDOWN_TIMEOUT ]; then
      STOP_MSG="⚠️ 강제 종료됨 (Time out 도달)"
  else
      STOP_MSG="✅ 정상 종료 성공"
  fi

  send_slack ">>> 🛑 구 버전 컨테이너 중지 완료: ${STOP_DURATION}초 소요 ($STOP_MSG)"
fi

send_slack ">>> 사용하지 않는 Docker 이미지 정리(Prune)..."
docker image prune -f

TOTAL_END_TIME=$(date +%s)
TOTAL_DURATION=$((TOTAL_END_TIME - TOTAL_START_TIME))

send_slack ">>> 🎉 *배포가 성공적으로 완료되었습니다!*
           (총 소요 시간: ${TOTAL_DURATION}초)

           [변경 전] \`${OLD_IMAGE_NAME}\`
           ⬇️
           [변경 후] \`${NEW_IMAGE_NAME}\`"