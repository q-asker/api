#!/bin/bash

# ==============================================================================
# 설정 변수
# ==============================================================================
# 1. 부팅 대기 설정 (새 버전이 뜰 때까지 기다리는 시간)
# 5초 간격으로 36번 시도 = 총 180초(3분) 대기
MAX_RETRIES=36
SLEEP_TIME=5

# 2. 종료 대기 설정 (구 버전이 꺼질 때까지 기다리는 시간)
# Graceful Shutdown을 위해 180초 동안 강제 종료하지 않음
SHUTDOWN_TIMEOUT=60


echo "🚀 Blue/Green Deployment Start..."

# 1. 현재 구동 중인 프로필 확인 (Actuator 또는 커스텀 API 활용)
# curl 접속이 안 되거나(첫 배포), 응답이 없으면 빈 값
# {"profile":"blue","port":8081,"status":"UP"} 파싱
CURRENT_PROFILE=$(curl -s --connect-timeout 3 http://localhost/status | grep -o '"profile":"[^"]*"' | cut -d'"' -f4)

# 2. 타겟 프로필 및 포트 설정
# 현재가 blue라면 -> 타겟은 green
# 현재가 green이거나, 실행 중이 아니면(첫 배포 등) -> 타겟은 blue
if [[ "$CURRENT_PROFILE" == *"blue"* ]]; then
  CURRENT_CONTAINER="app-blue"
  TARGET_PROFILE="green"
  TARGET_PORT=8082
  TARGET_CONTAINER="app-green"
else
  # "green"이거나, "null"(첫 배포)인 경우 blue를 타겟으로 설정
  CURRENT_CONTAINER="app-green"
  TARGET_PROFILE="blue"
  TARGET_PORT=8081
  TARGET_CONTAINER="app-blue"
fi

echo ">>> Current Profile : $CURRENT_PROFILE ($CURRENT_CONTAINER)"
echo ">>> Target Profile  : $TARGET_PROFILE ($TARGET_CONTAINER)"

# 3. 최신 이미지 Pull 및 컨테이너 실행
echo ">>> Docker Pull & Up ($TARGET_CONTAINER)..."
docker-compose pull $TARGET_CONTAINER
docker-compose up -d $TARGET_CONTAINER

# 4. 헬스 체크 (Spring Boot Actuator)
echo ">>> Health Check Start (Port: $TARGET_PORT)..."

for ((i=1; i<=MAX_RETRIES; i++)); do
  # Actuator health endpoint 호출
  RESPONSE=$(curl -s http://localhost:$TARGET_PORT/status)
  # 응답에 "status":"UP"이 포함되어 있는지 확인
  UP_CHECK=$(echo "$RESPONSE" | grep -o '"status":"UP"')

  if [ ! -z "$UP_CHECK" ]; then
    echo ">>> Health Check Success! (Attempt $i/$MAX_RETRIES)"
    break
  fi

  if [ $i -eq $MAX_RETRIES ]; then
    echo ">>> ❌ Health Check Failed after $MAX_RETRIES attempts."
    echo ">>> Response: $RESPONSE"
    echo ">>> Deployment Aborted. Stopping $TARGET_CONTAINER..."
    docker-compose stop $TARGET_CONTAINER
    exit 1
  fi

  echo ">>> Waiting for service... ($i/$MAX_RETRIES)"
  sleep $SLEEP_TIME
done

# 5. Nginx 트래픽 전환
echo ">>> Switching Nginx Traffic to $TARGET_CONTAINER..."

# service-url.inc 파일 내용을 타겟 컨테이너 주소로 덮어쓰기
echo "set \$service_url http://$TARGET_CONTAINER:8080;" > ./nginx/conf.d/service-url.inc

# Nginx 설정 리로드 (무중단 적용)
echo ">>> Reloading Nginx..."
docker exec nginx nginx -s reload

# 6. 이전 버전 컨테이너 중지
# 첫 배포(CURRENT_PROFILE이 비어있음)가 아닐 때만 중지 시도
if [ -n "$CURRENT_PROFILE" ]; then
  echo ">>> Stopping Old Container ($CURRENT_CONTAINER) with ${SHUTDOWN_TIMEOUT}s timeout..."
  # [-t 초] 옵션: 컨테이너에게 종료 신호를 보내고 지정된 시간만큼 기다림
  docker-compose stop -t $SHUTDOWN_TIMEOUT $CURRENT_CONTAINER
fi

echo ">>> 🎉 Deployment Completed Successfully!"