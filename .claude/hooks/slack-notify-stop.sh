#!/bin/bash
# Claude Code 작업 완료 시 Slack 알림 전송

INPUT=$(cat)

# stop_hook_active가 true이면 중복 알림 방지
if echo "$INPUT" | jq -e '.stop_hook_active == true' > /dev/null 2>&1; then
  exit 0
fi

CWD=$(echo "$INPUT" | jq -r '.cwd // "unknown"')

# 환경변수 미설정 시 프로젝트 .env 파일에서 로드
if [ -z "$SLACK_WEBHOOK_URL" ] && [ -f "$CWD/.env" ]; then
  SLACK_WEBHOOK_URL=$(grep -E '^SLACK_WEBHOOK_URL=' "$CWD/.env" | cut -d'=' -f2-)
fi
[ -z "$SLACK_WEBHOOK_URL" ] && exit 0

# jq 단일 파이프라인: 입력 파싱 → truncate → 페이로드 생성
PAYLOAD=$(echo "$INPUT" | jq '{
  project: (.cwd // "" | split("/") | last // "unknown"),
  summary: ((.last_assistant_message // "작업이 완료되었습니다.") | if length > 200 then .[:200] + "..." else . end)
} | {
  blocks: [
    {
      type: "header",
      text: { type: "plain_text", text: "✅ Claude Code 작업 완료", emoji: true }
    },
    {
      type: "section",
      fields: [
        { type: "mrkdwn", text: "*프로젝트:*\n\(.project)" }
      ]
    },
    {
      type: "section",
      text: { type: "mrkdwn", text: "*요약:*\n\(.summary)" }
    }
  ]
}')

curl -s -X POST "$SLACK_WEBHOOK_URL" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD" \
  --max-time 5 \
  > /dev/null 2>&1

exit 0
