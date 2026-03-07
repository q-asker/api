#!/bin/bash
# Claude Code 권한 요청 시 Slack 알림 전송

INPUT=$(cat)
CWD=$(echo "$INPUT" | jq -r '.cwd // "unknown"')

# 환경변수 미설정 시 프로젝트 .env 파일에서 로드
if [ -z "$SLACK_WEBHOOK_URL" ] && [ -f "$CWD/.env" ]; then
  SLACK_WEBHOOK_URL=$(grep -E '^SLACK_WEBHOOK_URL=' "$CWD/.env" | cut -d'=' -f2-)
fi
[ -z "$SLACK_WEBHOOK_URL" ] && exit 0

# jq 단일 파이프라인: 입력 파싱 → 페이로드 생성
PAYLOAD=$(echo "$INPUT" | jq '{
  message: (.message // "권한 요청"),
  tool: ((.message // "") | capture("to use (?<t>.+)$") // {t: "unknown"} | .t),
  project: (.cwd // "" | split("/") | last // "unknown")
} | {
  blocks: [
    {
      type: "header",
      text: { type: "plain_text", text: "🚨 Claude Code 권한 요청", emoji: true }
    },
    {
      type: "section",
      fields: [
        { type: "mrkdwn", text: "*프로젝트:*\n\(.project)" },
        { type: "mrkdwn", text: "*도구:*\n`\(.tool)`" }
      ]
    },
    {
      type: "section",
      text: { type: "mrkdwn", text: "*메시지:*\n\(.message)" }
    }
  ]
}')

curl -s -X POST "$SLACK_WEBHOOK_URL" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD" \
  --max-time 5 \
  > /dev/null 2>&1

exit 0
