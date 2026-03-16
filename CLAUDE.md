# Q-Asker API

## 프로젝트 개요

AI 기반 퀴즈 자동 생성 및 관리 플랫폼의 백엔드 API 서버. 사용자가 업로드한 문서(PDF, PPT, DOCX)를 기반으로 Google Gemini AI가 퀴즈를 자동
생성하고, OAuth2 소셜 로그인과 JWT 인증을 제공한다.

## 기술 스택

- **언어**: Java 21
- **프레임워크**: Spring Boot 3.5.8
- **빌드 도구**: Gradle (Groovy DSL)
- **ORM**: Spring Data JPA + Hibernate
- **DB**: MySQL
- **인증**: Auth0 Java JWT 4.5.0, Spring OAuth2 Client (Google, Kakao)
- **AI**: Spring AI 1.1.2 + Google Gemini
- **클라우드**: AWS SDK 2.27.24 (S3, CloudFront)
- **메시징**: Spring Kafka
- **API 문서**: SpringDoc OpenAPI 2.8.8 (Swagger UI)
- **장애 대응**: Resilience4j 2.3.0 (Circuit Breaker)
- **모니터링**: Scouter APM, Spring Actuator, Micrometer Prometheus
- **컨테이너**: Jib 3.4.0 (Docker 빌드)
- **코드 포맷**: Spotless 7.0.4 + Google Java Format 1.25.2
- **문서 변환**: JODConverter 4.4.9 (LibreOffice 기반 PPT/DOCX → PDF 변환)
- **공통 라이브러리**: Lombok, Bean Validation, Hashids, Apache HttpClient 5

## 명령어 (Scripts)

```bash
# 빌드
./gradlew build                    # 전체 빌드 (컴파일 + 테스트)
./gradlew build --parallel         # 병렬 빌드

# 실행
./gradlew :app:bootRun             # 로컬 서버 실행

# 포맷팅
./gradlew spotlessApply            # 코드 포맷 자동 적용
./gradlew spotlessCheck            # 코드 포맷 검증 (CI용)

# Docker
./gradlew jib                      # Docker 이미지 빌드 및 푸시
./gradlew jibDockerBuild           # 로컬 Docker 이미지 빌드

# 테스트
./gradlew test                     # 전체 테스트 실행
```

## 아키텍처

### 멀티모듈 구조

```
q-asker/api/
├── app/                           # 실행 모듈 (Spring Boot main, 설정)
├── modules/
│   ├── global/                    # 공통 모듈 (BaseEntity, ApiResponse, GlobalExceptionHandler)
│   ├── auth/
│   │   ├── api/                   # 인증 인터페이스, DTO
│   │   └── impl/                  # JWT, OAuth2 구현
│   ├── quiz/
│   │   ├── api/                   # 퀴즈 인터페이스, DTO
│   │   └── impl/                  # 퀴즈 CRUD, 출제/채점 로직
│   ├── ai/
│   │   ├── api/                   # AI 인터페이스, DTO
│   │   └── impl/                  # Google Gemini 연동
│   ├── aws/
│   │   ├── api/                   # AWS 인터페이스
│   │   └── impl/                  # S3 파일 업로드/다운로드
│   ├── board/
│   │   ├── api/                   # 게시판 인터페이스, DTO
│   │   └── impl/                  # 게시판 로직, Kafka 연동
│   └── util/
│       ├── api/                   # 유틸리티 인터페이스
│       └── impl/                  # 헬스체크 등
├── docs/                          # 문서 (PRD, ROADMAP, 리뷰)
├── .github/workflows/             # CI/CD (ci-check-code-convention.yml, cd-prod_deploy.yml, ci-auto-version-bump.yml, ci-update-api-docs.yml)
├── .githooks/                     # Git 훅 (pre-commit, prepare-commit-msg)
├── infra/                          # 인프라 설정 (Docker, 모니터링)
│   ├── Dockerfile                 # Docker 이미지 빌드
│   ├── docker-compose.yml         # 로컬 개발 환경 (MySQL, Scouter)
│   └── monitoring/                # Prometheus, Alloy 모니터링 설정
└── .claude/                       # Claude Code 설정
```

### 모듈 의존성

```
app → *-impl → *-api → global
```

- `app`: 모든 impl 모듈을 조립하여 실행 가능 애플리케이션 구성
- `*-api`: 인터페이스, DTO만 정의 (구현체 금지)
- `*-impl`: 비즈니스 로직, Repository, Entity
- `global`: 전역 공통 코드 (BaseEntity, ApiResponse, 예외 처리)

## 환경 변수

### Docker Compose (.env)

| 변수                    | 용도              |
|-----------------------|-----------------|
| `MYSQL_ROOT_PASSWORD` | MySQL root 비밀번호 |
| `MYSQL_DATABASE`      | 데이터베이스 이름       |
| `MYSQL_USER`          | MySQL 사용자       |
| `MYSQL_PASSWORD`      | MySQL 비밀번호      |

### application.yml 주요 설정

- `spring.datasource.*`: DB 연결 정보
- `spring.security.oauth2.client.*`: OAuth2 소셜 로그인 (Google, Kakao)
- `spring.security.jwt.*`: JWT 시크릿, 만료 시간
- `spring.ai.google.genai.*`: Gemini API 키, 모델, 온도
- `aws.s3.*`: S3 리전, 버킷, 인증 정보
- `q-asker.slack.*`: Slack 알림 웹훅
- `q-asker.web.*`: 프론트엔드 URL
- `jodconverter.local.*`: JODConverter 설정 (LibreOffice 연동)
- `resilience4j.circuitbreaker.*`: Circuit Breaker 설정

### Jib 배포 시 필요 프로퍼티 (-P 옵션)

- `DOCKER_ID`, `DOCKER_IMAGE_NAME`, `DOCKER_PASSWORD`
- `JVM_HEAP_SIZE`, `JVM_GC_TYPE`, `JVM_MAX_GC_PAUSE_MILLIS`
- `SCOUTER_IP`, `SCOUTER_PORT`, `SCOUTER_OBJ_NAME`

## 개발 도구 및 설정

- **패키지 매니저**: Gradle 래퍼 (`./gradlew`)
- **JDK**: 21 (Eclipse Temurin)
- **포맷터**: Spotless + Google Java Format (`./gradlew spotlessApply`)
- **CI**: GitHub Actions — PR 시 `spotlessCheck` + `build`
- **Git Hooks**: `.githooks/` 디렉토리 (`core.hooksPath` 설정 완료)
    - `pre-commit`: Spotless 포맷 검증
    - `prepare-commit-msg`: JIRA 티켓 접두사 자동 추가
- **로컬 인프라**: Docker Compose (MySQL, Scouter)
- **API 문서**: Swagger UI (`/swagger-ui/index.html`), Redoc (GitHub Pages 자동 배포)
