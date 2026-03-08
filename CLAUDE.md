# CLAUDE.md

<!-- TODO: docs/PRD.md 생성 후 활성화 -->
<!-- > 제품 요구사항은 docs/PRD.md 참조. -->

<!-- TODO: docs/roadmaps/ 생성 후 활성화 -->
<!-- > 로드맵은 docs/roadmaps/ROADMAP_v*.md 참조. -->

## 프로젝트 개요

Q-Asker는 AI 기반 퀴즈 생성·출제·채점 플랫폼의 백엔드 API 서버이다. Spring Boot 멀티모듈 구조로 퀴즈 CRUD, 소셜 로그인, 파일 업로드, AI 퀴즈 생성 기능을 제공한다.

## 기술 스택

| 분류       | 기술                                  | 버전            |
| ---------- | ------------------------------------- | --------------- |
| 언어       | Java                                  | 21              |
| 프레임워크 | Spring Boot                           | 3.5.8           |
| AI         | Spring AI + Google Gemini             | 1.1.2           |
| ORM        | Spring Data JPA + Hibernate           | (BOM 관리)      |
| DB         | MySQL                                 | latest (Docker) |
| 인증       | Auth0 Java JWT + Spring OAuth2 Client | 4.5.0           |
| AWS        | AWS SDK (S3)                          | 2.27.24         |
| 장애 대응  | Resilience4j                          | 2.3.0           |
| API 문서   | SpringDoc OpenAPI (Swagger UI)        | 2.8.8           |
| 모니터링   | Scouter APM                           | -               |
| 로깅       | SLF4J + Logback + Slack Appender      | 1.6.1           |
| 빌드       | Gradle                                | 8.14.3          |
| 컨테이너   | Jib (Docker 이미지 빌드)              | 3.4.0           |
| 포맷터     | Spotless + Google Java Format         | 7.0.4           |
| 유틸리티   | Lombok, Hashids, Janino               | -               |

## 명령어 (Scripts)

```bash
# 빌드
./gradlew build                    # 전체 빌드 (컴파일 + 테스트)
./gradlew clean build              # 클린 빌드

# 실행
./gradlew :app:bootRun             # 로컬 실행

# 테스트
./gradlew test                     # 전체 테스트 실행
./gradlew :quiz:quiz-impl:test     # 특정 모듈 테스트

# Docker
docker-compose up -d               # MySQL + Scouter 컨테이너 실행
docker-compose down                # 컨테이너 중지

# 포맷팅
./gradlew spotlessApply             # 코드 자동 포맷팅 (Google Java Format)
./gradlew spotlessCheck             # 포맷 위반 검증 (CI용)

# Jib (Docker 이미지 빌드 — CI/CD에서 사용)
./gradlew jib -PDOCKER_ID=... -PDOCKER_PASSWORD=... -PDOCKER_IMAGE_NAME=... -PJVM_HEAP_SIZE=... -PSCOUTER_IP=... -PSCOUTER_PORT=... -PSCOUTER_OBJ_NAME=...
```

## 아키텍처

### 멀티모듈 구조

```
q-asker/
├── app/                          # 부트스트랩 모듈 (Spring Boot 메인 클래스)
│   └── src/main/resources/       # application.yml, application-local.yml, application-prod.yml
├── modules/
│   ├── global/                   # 전역 예외 처리, 공통 응답 DTO, BaseEntity
│   ├── auth/
│   │   ├── api/                  # 인증 인터페이스, DTO, 예외
│   │   └── impl/                 # JWT 발급·검증, OAuth2 소셜 로그인 구현
│   ├── aws/
│   │   ├── api/                  # AWS 인터페이스, DTO
│   │   └── impl/                 # S3 파일 업로드·다운로드 구현
│   ├── quiz/
│   │   ├── api/                  # 퀴즈 인터페이스, DTO, 예외
│   │   └── impl/                 # 퀴즈 CRUD, 출제·채점 로직 구현
│   ├── ai/
│   │   ├── api/                  # AI 인터페이스, DTO
│   │   └── impl/                 # Spring AI + Gemini 기반 퀴즈 생성
│   └── util/
│       ├── api/                  # 유틸 인터페이스
│       └── impl/                 # 헬스체크 등 범용 기능
├── build.gradle                  # 루트 빌드 설정 (BOM, 공통 의존성)
├── settings.gradle               # 모듈 등록
├── docker-compose.yml            # MySQL + Scouter 로컬 환경
└── .github/workflows/            # CI/CD (auto-version-bump, prod_deploy)
```

### 모듈 의존 방향

```
app → 모든 impl 모듈 + global
impl → 자신의 api + global + 다른 모듈의 api (다른 impl 직접 의존 금지)
api → (의존 없음 또는 global만)
```

### 패키지 구조

- 베이스 패키지: `com.icc.qasker`
- 모듈별: `com.icc.qasker.{도메인}` (예: `com.icc.qasker.quiz`, `com.icc.qasker.auth`)

## 환경 변수

### .env (Docker Compose용 — Git 미추적)

| 키                    | 설명                 |
| --------------------- | -------------------- |
| `MYSQL_ROOT_PASSWORD` | MySQL root 비밀번호  |
| `MYSQL_DATABASE`      | MySQL 데이터베이스명 |
| `MYSQL_USER`          | MySQL 사용자명       |
| `MYSQL_PASSWORD`      | MySQL 비밀번호       |

### application-local.yml / application-prod.yml (Git 미추적)

프로필별 DB 연결 정보, JWT 시크릿, AWS 자격증명, AI API 키 등을 관리한다.

## 개발 도구 및 설정

| 도구                 | 설명                                                          |
| -------------------- | ------------------------------------------------------------- |
| Gradle 8.14.3        | 빌드 도구 (Wrapper 사용)                                      |
| JDK 21               | Gradle toolchain으로 자동 관리                                |
| Docker Compose       | 로컬 MySQL + Scouter 컨테이너                                 |
| Jib 3.4.0            | Dockerfile 없는 Docker 이미지 빌드                            |
| Scouter APM          | 성능 모니터링 (로컬 + 운영)                                   |
| GitHub Actions       | CI/CD (auto-version-bump, prod_deploy)                        |
| SpringDoc Swagger UI | `/swagger-ui/index.html`에서 API 테스트                       |
| Spotless 7.0.4       | Google Java Format 자동 포맷팅 (PostToolUse 훅으로 자동 실행) |

## CLAUDE.md 유지 규칙

이 파일은 프로젝트의 Single Source of Truth이다.
아래 변경 발생 시 CLAUDE.md를 반드시 함께 갱신한다.

| 변경 사항                       | 갱신 대상 섹션    |
| ------------------------------- | ----------------- |
| 명령어(scripts) 추가/변경/삭제  | 명령어 (Scripts)  |
| 기술 스택/주요 패키지 추가/변경 | 기술 스택         |
| 아키텍처, 라우팅 구조 변경      | 아키텍처          |
| 환경 변수 추가/변경             | 환경 변수         |
| 개발 도구 및 설정 변경          | 개발 도구 및 설정 |
| 디렉토리 구조/컨벤션 변경       | 아키텍처          |
