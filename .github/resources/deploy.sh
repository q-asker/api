#!/bin/bash

# Slack 전송 함수
WEBHOOK="${SLACK_WEBHOOK_URL:?SLACK_WEBHOOK_URL is required}"

function send_slack() {
  local MESSAGE="$1"
  local USERNAME="배포 상태 알림이"
  local ICON=":gear:"

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
  curl --connect-timeout 3 --max-time 5 -s -X POST -H 'Content-type: application/json' --data "$PAYLOAD" "$WEBHOOK" > /dev/null
}

# ==============================================================================
# 설정 변수
# ==============================================================================
MAX_RETRIES=36
SLEEP_TIME=5
SHUTDOWN_TIMEOUT=60
BLUE_PORT="${1:?BLUE_PORT is required}"
GREEN_PORT="${2:?GREEN_PORT is required}"
# 배포 시작 시간 기록
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

send_slack ">>> 현재 컨테이너 : $CURRENT_CONTAINER"
send_slack ">>> 띄울 컨테이너 : $TARGET_CONTAINER"

# 3. 최신 이미지 Pull 및 컨테이너 실행
send_slack ">>> Docker Pull & Up ($TARGET_CONTAINER)..."
docker compose pull $TARGET_CONTAINER
docker compose up -d $TARGET_CONTAINER

# 4. 헬스 체크
send_slack ">>> Health Check Start (Port: $TARGET_PORT)..."

# 헬스 체크 시작 시간 기록
HEALTH_START_TIME=$(date +%s)

for ((i=1; i<=MAX_RETRIES; i++)); do
  # status endpoint 호출
  RESPONSE=$(curl -s --connect-timeout 3 --max-time 5 http://localhost:$TARGET_PORT/status)
  # 응답에 "status":"UP"이 포함되어 있는지 확인
  UP_CHECK=$(echo "$RESPONSE" | grep -o '"status":"UP"')

  if [ ! -z "$UP_CHECK" ]; then
    # 성공 시 종료 시간 기록 및 소요 시간 계산
    HEALTH_END_TIME=$(date +%s)
    HEALTH_DURATION=$((HEALTH_END_TIME - HEALTH_START_TIME))

    # 슬랙 알림에 부팅 소요 시간 포함
    send_slack ">>> ✅ Health Check Passed! (Startup Time: ${HEALTH_DURATION}s)"
    break
  fi
  if [ $i -eq $MAX_RETRIES ]; then
    echo ">>> ❌ Health Check Failed after $MAX_RETRIES attempts."
    echo ">>> "
    send_slack ">>> ⚠️ 배포 실패!! Stopping $TARGET_CONTAINER... \nResponse: $RESPONSE"
    docker compose stop $TARGET_CONTAINER
    exit 1
  fi

  send_slack ">>> ($TARGET_CONTAINER) 응답 대기중... ($i/$MAX_RETRIES)"
  sleep $SLEEP_TIME
done

# 5. Nginx 트래픽 전환
send_slack ">>> Switching Nginx Traffic to $TARGET_CONTAINER..."
echo "set \$service_url http://$TARGET_CONTAINER:8080;" > ./nginx/conf.d/service-url.inc

# Nginx 컨테이너 실행 여부 확인 및 실행
IS_NGINX_RUNNING=$(docker ps | grep nginx)

if [ -z "$IS_NGINX_RUNNING" ]; then
    send_slack ">>> Nginx is not running. Starting Nginx..."
    docker compose up -d nginx
else
    send_slack ">>> Nginx is running. Reloading..."
    docker exec nginx nginx -s reload
fi

OLD_IMAGE_NAME="없음 (최초 배포)"
# 6. 이전 버전 컨테이너 중지
if [ -n "$CURRENT_PROFILE" ]; then
  send_slack ">>> Stopping Old Container ($CURRENT_CONTAINER) with ${SHUTDOWN_TIMEOUT}s timeout..."
  OLD_IMAGE_NAME=$(docker inspect --format='{{.Config.Image}}' $CURRENT_CONTAINER)

  STOP_START=$(date +%s)
  docker compose stop -t $SHUTDOWN_TIMEOUT $CURRENT_CONTAINER
  STOP_END=$(date +%s)
  STOP_DURATION=$((STOP_END - STOP_START))

  if [ $STOP_DURATION -ge $SHUTDOWN_TIMEOUT ]; then
      STOP_MSG="⚠️ 강제 종료됨 (Time out 도달)"
  else
      STOP_MSG="✅ Graceful Shutdown 성공"
  fi

  send_slack ">>> 🛑 Old Container Stopped: ${STOP_DURATION}s 소요 ($STOP_MSG)"
fi

send_slack ">>> Pruning unused Docker images..."
docker image prune -f

TOTAL_END_TIME=$(date +%s)
TOTAL_DURATION=$((TOTAL_END_TIME - TOTAL_START_TIME))
NEW_IMAGE_NAME="${3:?NEW_IMAGE_NAME is required}"

send_slack ">>> 🎉 Deployment Completed Successfully! \n${OLD_IMAGE_NAME}->${NEW_IMAGE_NAME} \n(Total Time: ${TOTAL_DURATION}s)"