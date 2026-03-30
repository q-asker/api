#!/bin/bash
# Let's Encrypt SSL 인증서 초기 발급 스크립트
# 서버에서 최초 1회만 실행

set -e

DOMAIN="api.q-asker.com"
EMAIL="${1:?이메일 주소를 입력하세요 (예: ./init-ssl.sh admin@example.com)}"

echo "=== Let's Encrypt SSL 인증서 발급 ==="
echo "도메인: $DOMAIN"
echo "이메일: $EMAIL"

# 1. certbot 설치 (없는 경우)
if ! command -v certbot &> /dev/null; then
    echo ">>> certbot 설치 중..."
    sudo apt-get update
    sudo apt-get install -y certbot
fi

# 2. ACME challenge 디렉토리 생성
sudo mkdir -p /var/www/certbot

# 3. 80 포트 사용 중인 컨테이너 일시 중지
echo ">>> 80 포트 해제를 위해 nginx 컨테이너 중지..."
docker compose stop nginx 2>/dev/null || true

# 4. standalone 모드로 인증서 발급
echo ">>> 인증서 발급 중..."
sudo certbot certonly \
    --standalone \
    --preferred-challenges http \
    -d "$DOMAIN" \
    --email "$EMAIL" \
    --agree-tos \
    --non-interactive

# 5. 인증서 자동 갱신 cron 등록
echo ">>> 인증서 자동 갱신 cron 등록..."
CRON_CMD="0 3 * * * certbot renew --webroot -w /var/www/certbot --quiet && docker exec nginx nginx -s reload"
(crontab -l 2>/dev/null | grep -v "certbot renew"; echo "$CRON_CMD") | crontab -

echo "=== 인증서 발급 완료 ==="
echo "인증서 경로: /etc/letsencrypt/live/$DOMAIN/"
echo "자동 갱신: 매일 03:00 (cron)"
echo ""
echo "이제 docker compose up -d nginx 로 Nginx를 시작하세요."
