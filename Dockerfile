# eclipse-temurin:21-jdk-noble: arm64v8 공식 지원, Ubuntu 24.04(Noble) 기반
# bookworm(Debian) 태그는 deprecated → noble 사용
FROM eclipse-temurin:21-jdk-noble

# ── 시스템 패키지 설치 ──────────────────────
# DEBIAN_FRONTEND=noninteractive: apt 설치 중 대화형 프롬프트 방지
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    # LibreOffice 핵심 컴포넌트 (헤드리스 변환에 필요한 것만)
    libreoffice-core \
    libreoffice-writer \
    libreoffice-calc \
    libreoffice-impress \
    libreoffice-draw \
    libreoffice-math \
    # 한국어/CJK 폰트 (한글 문서 변환 깨짐 방지)
    fonts-noto-cjk \
    # MS Office 호환 폰트 (Liberation = Arial/Times/Courier 대체)
    fonts-liberation \
    fonts-dejavu \
    # JODConverter가 LibreOffice 프로세스 PID 추적에 사용하는 유틸
    procps \
    # 폰트 캐시 재빌드 도구
    fontconfig \
    # LibreOffice가 일부 환경에서 요구하는 최소 X 라이브러리 (디스플레이 없이도 필요)
    libxrender1 \
    libxext6 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* \
    # 폰트 캐시 재빌드
    && fc-cache -fv

# ── 비루트 사용자 생성 ───────────────────────
# 컨테이너 보안 원칙: root 탈출 시 호스트 피해 최소화
# LibreOffice 프로필 생성을 위해 홈 디렉토리(-m) 필수
RUN groupadd -r appuser && useradd -r -g appuser -m -d /home/appuser appuser

# ── 환경변수 ─────────────────────────────────
# Jib이 이 베이스 이미지 위에 앱 레이어를 얹을 때 HOME이 설정되어 있어야 함
ENV HOME=/home/appuser
