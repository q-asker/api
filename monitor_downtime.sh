#!/bin/bash

# ==========================================
# 설정 (본인 환경에 맞게 수정하세요)
# ==========================================
URL="http://dev-api.q-asker.com:8080/actuator/info"
SLEEP_SEC=0.1
# ==========================================

echo "Checking initial version from $URL..."

# 1. 초기 버전 확인 (현재 실행 중인 버전)
INITIAL_RESPONSE=$(curl -s -m 2 "$URL")
if [ -z "$INITIAL_RESPONSE" ]; then
    echo "Error: Cannot connect to server to get initial version."
    exit 1
fi

OLD_VERSION=$(echo "$INITIAL_RESPONSE" | jq -r '.build.version')

if [ "$OLD_VERSION" == "null" ] || [ -z "$OLD_VERSION" ]; then
    echo "Error: Could not parse version from JSON. Check if 'build.version' exists."
    echo "Response: $INITIAL_RESPONSE"
    exit 1
fi

echo "-----------------------------------------------------"
echo "TARGET URL      : $URL"
echo "CURRENT VERSION : $OLD_VERSION"
echo "Scanning for new version every $SLEEP_SEC seconds..."
echo "-----------------------------------------------------"

# 변수 초기화
START_TIME=$(date +%s.%N)
DOWNTIME_START=0
DOWNTIME_END=0
IS_DOWN=false

while true; do
    # 현재 시각 (밀리초 단위 표시용)
    NOW=$(date "+%H:%M:%S")

    # 요청 수행 (-s: 조용히, -m 1: 타임아웃 1초)
    RESPONSE=$(curl -s -m 1 "$URL")
    EXIT_CODE=$?

    # 2. 서버가 응답하지 않거나(다운됨) 에러인 경우
    if [ $EXIT_CODE -ne 0 ] || [ -z "$RESPONSE" ]; then
        if [ "$IS_DOWN" = false ]; then
            # 다운타임 시작 시간 기록
            DOWNTIME_START=$(date +%s.%N)
            IS_DOWN=true
            echo "[$NOW] 🔴 Service DOWN (Connection refused or Timeout)"
        else
            # 계속 다운 상태
            echo -ne "[$NOW] 🔴 Service DOWN...\r"
        fi

    else
        # 3. 응답이 성공한 경우 JSON 파싱
        NEW_VERSION=$(echo "$RESPONSE" | jq -r '.build.version')

        # 버전이 null이면 아직 부트 중이거나 데이터가 덜 로드된 상태 (잠재적 다운타임)
        if [ "$NEW_VERSION" == "null" ]; then
             if [ "$IS_DOWN" = false ]; then
                DOWNTIME_START=$(date +%s.%N)
                IS_DOWN=true
             fi
             echo "[$NOW] 🟡 Service UP but Version info missing..."

        # 4. 새 버전 감지!
        elif [ "$NEW_VERSION" != "$OLD_VERSION" ]; then
            # 다운타임이 있었다면 종료 시간 기록
            if [ "$IS_DOWN" = true ]; then
                DOWNTIME_END=$(date +%s.%N)
            fi

            echo ""
            echo "-----------------------------------------------------"
            echo "[$NOW] 🟢 NEW VERSION DETECTED!"
            echo "Old Version: $OLD_VERSION"
            echo "New Version: $NEW_VERSION"

            # 다운타임 계산 및 출력
            if [ "$IS_DOWN" = true ]; then
                DURATION=$(echo "$DOWNTIME_END - $DOWNTIME_START" | bc)
                printf "⏱️  Actual Downtime: %.3f seconds\n" "$DURATION"
            else
                echo "⏱️  Zero Downtime (No connection loss detected)"
            fi
            echo "-----------------------------------------------------"
            break

        # 5. 여전히 구 버전인 경우
        else
            if [ "$IS_DOWN" = true ]; then
                # 다운되었다가 다시 구 버전이 뜬 경우 (재시작 실패 등) -> 다운타임 종료로 처리할지 결정 필요하나 여기선 리셋
                IS_DOWN=false
                echo "[$NOW] 🟠 Recovered to OLD version ($OLD_VERSION)"
            else
                echo -ne "[$NOW] 🔵 Still Old Version ($OLD_VERSION)...\r"
            fi
        fi
    fi

    sleep $SLEEP_SEC
done