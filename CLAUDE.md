# Q-Asker API

## 프로젝트 개요

문서(PDF, PPT, DOCX)를 업로드하면 Google Gemini AI가 자동으로 퀴즈를 생성하는 Spring Boot 기반 백엔드 API 서버. SSE를 통한 실시간 생성 스트리밍, 퀴즈 세트 관리, 풀이 히스토리 기록을 지원한다.

## 기술 스택

| 분류 | 기술 | 버전 |
|---|---|---|
| 언어 | Java | 21 |
| 프레임워크 | Spring Boot | 3.5.8 |
| AI | Spring AI (Google Gemini via Vertex AI) | 1.1.2 |
| ORM | Spring Data JPA + Hibernate | (Boot BOM) |
| DB | MySQL | - |
| 인증 | JWT (Auth0 java-jwt 4.5.0) + OAuth2 Client | - |
| 클라우드 | OCI Java SDK (Object Storage) + Cloudflare CDN + Google Cloud Storage | 3.80.3 |
| 문서변환 | JODConverter (LibreOffice) | 4.4.9 |
| 모니터링 | Micrometer + Prometheus + Actuator | (Boot BOM) |
| 장애격리 | Resilience4j (Circuit Breaker) | 2.3.0 |
| Rate Limiting | Bucket4j + Caffeine | 8.16.1 |
| API 문서 | SpringDoc OpenAPI (Swagger UI) | 2.8.8 |
| 암호화 | Jasypt | 3.0.5 |
| ID 난독화 | Hashids | 1.0.3 |
| 빌드 | Gradle (Groovy DSL) | 8.14.3 |
| 컨테이너 | Jib (Docker) | 3.4.0 |
| 포맷터 | Spotless + Google Java Format | 7.0.4 / 1.25.2 |
| DB 마이그레이션 | Flyway | (Boot BOM) |
| 테스트 | JUnit 5 | (Boot BOM) |

## 명령어 (Scripts)

```bash
# 빌드
./gradlew build              # 전체 빌드 (컴파일 + 테스트 + JAR)
./gradlew :app:bootJar       # 실행 가능 JAR 생성
./gradlew :app:bootRun       # 로컬 실행

# 테스트
./gradlew test               # 전체 테스트
./gradlew :모듈명:test       # 특정 모듈 테스트

# 포맷팅
./gradlew spotlessApply      # 코드 포맷 적용 (Google Java Format)
./gradlew spotlessCheck      # 포맷 위반 검증

# Docker
./gradlew jib                # Docker 이미지 빌드 + 푸시
./gradlew jibDockerBuild     # 로컬 Docker 이미지 빌드

# 유틸리티
./gradlew installGitHooks            # Git hooks 경로 설정
./gradlew dependencyGraphStyled      # 모듈 의존성 그래프 생성 (SVG)
```

## 아키텍처

### 멀티모듈 구조

의존 방향: `app` → `*-impl` → `*-api` → `global`

```
q-asker/api/
├── app/                          # 진입점 (Spring Boot Application)
│   └── src/main/resources/
│       ├── application.yml       # 설정 진입점 (config/ import)
│       ├── application-secrets.yml  # 암호화된 시크릿
│       └── config/               # 분리된 설정 파일들
│           ├── server.yml        # 서버, DB, JPA, 캐시
│           ├── ai.yml            # Google Gemini AI 설정
│           ├── security.yml      # JWT, OAuth2, CORS
│           ├── aws.yml           # OCI Object Storage, CDN
│           ├── jodconverter.yml  # LibreOffice 문서변환
│           ├── monitoring.yml    # Actuator, Prometheus
│           ├── q-asker.yml       # 앱 커스텀 설정
│           ├── resilience4j.yml  # Circuit Breaker
│           └── springdoc.yml     # Swagger/OpenAPI
├── modules/
│   ├── global/                   # 공통 (BaseEntity, ApiResponse, GlobalExceptionHandler)
│   ├── auth/     (api + impl)    # 인증 (JWT, OAuth2, RateLimitFilter)
│   ├── oci/      (api + impl)    # OCI Object Storage 파일 업로드
│   ├── board/    (api + impl)    # 게시판
│   ├── quiz-ai/  (api + impl)    # AI 퀴즈 생성 (Gemini 호출, 메트릭)
│   ├── quiz-make/(api + impl)    # 퀴즈 생성 흐름 (파일업로드, SSE, 생성결과)
│   ├── quiz-set/ (api + impl)    # 퀴즈 세트 CRUD
│   ├── quiz-history/(api + impl) # 풀이 히스토리
│   └── util/     (api + impl)    # 유틸리티 (문서변환)
├── infra/
│   ├── monitoring/               # Grafana Alloy 설정
│   ├── mysql/                    # MySQL Docker 설정
│   ├── base-image/               # Docker 베이스 이미지
│   └── terraform/
│       ├── gcp/                  # GCP 인프라 (GCS, IAM, Vertex AI)
│       └── oci/                  # OCI 인프라 (NSG Cloudflare 인바운드 규칙)
├── docs/                         # 문서, 분석 자료
├── .githooks/                    # Git 훅 (pre-commit, pre-push, prepare-commit-msg)
└── .github/workflows/            # CI/CD
    ├── cd-prod_deploy.yml
    ├── ci-auto-version-bump.yml
    ├── ci-check-code-convention.yml
    └── ci-update-api-docs.yml
```

## 환경 변수

- 민감한 값은 `application-secrets.yml`에 Jasypt `ENC()`로 암호화하여 관리
- Jasypt 복호화 키: `JASYPT_PASSWORD` 환경변수 또는 JVM 옵션으로 전달
- 프로파일: `local` (개발), `prod` (운영)
- Actuator 포트: 9090 (서비스 포트와 분리)
- Virtual Threads 활성화 (`spring.threads.virtual.enabled: true`)
- OCI Object Storage: `~/.oci/config` 파일 기반 인증, `OCI_NAMESPACE`/`OCI_BUCKET_NAME` 환경변수
- Google Cloud: Vertex AI + GCS (ADC 인증)
  - `spring.ai.google.genai.project-id`: GCP 프로젝트 ID
  - `spring.ai.google.genai.location`: GCP 엔드포인트 (현재: `global`)
  - `GCS_BUCKET_NAME`: GCS 버킷 이름 (기본값: `q-asker-ai-files`)
  - 로컬: `gcloud auth application-default login`, 프로덕션: 서비스 계정
- DDoS 방어: Cloudflare Free (`api.q-asker.com`만 프록시 활성화)
- SSL/HTTPS: Cloudflare (Universal SSL) → Nginx (Origin CA TLS), Full (Strict) 모드
  - Origin 인증서: Cloudflare Origin CA (15년 유효)
  - 인증서 경로: `/etc/ssl/cloudflare/api.q-asker.com.pem`, `.key`
  - OCI NSG: 80/443 인바운드 Cloudflare IP 대역만 허용

## 개발 도구 및 설정

- **빌드**: Gradle 8.14.3 (Groovy DSL)
- **JDK**: 21 (Gradle Toolchain 자동 관리)
- **포맷터**: Spotless + Google Java Format 1.25.2
  - `./gradlew spotlessApply` — 포맷 적용
  - `./gradlew spotlessCheck` — 포맷 검증
- **Git Hooks** (`.githooks/`)
  - `prepare-commit-msg` — 브랜치에서 JIRA 티켓(`[A-Z]+-[0-9]+`) 감지하여 커밋 메시지 접두사 자동 추가
  - `pre-commit` — `spotlessCheck` 실행, 위반 시 커밋 차단
  - `pre-push` — `spotlessCheck` 실행, 위반 시 푸시 차단
- **CI/CD**: GitHub Actions
  - `ci-check-code-convention.yml` — PR 포맷 검증
  - `ci-auto-version-bump.yml` — 자동 버전 범프
  - `ci-update-api-docs.yml` — OpenAPI 스펙 자동 갱신
  - `cd-prod_deploy.yml` — 운영 배포

## gstack

- 모든 웹 브라우징은 gstack의 `/browse` 스킬을 사용한다. `mcp__claude-in-chrome__*` 도구는 사용하지 않는다.
- 사용 가능한 스킬: `/office-hours`, `/plan-ceo-review`, `/plan-eng-review`, `/plan-design-review`, `/design-consultation`, `/design-shotgun`, `/design-html`, `/review`, `/ship`, `/land-and-deploy`, `/canary`, `/benchmark`, `/browse`, `/connect-chrome`, `/qa`, `/qa-only`, `/design-review`, `/setup-browser-cookies`, `/setup-deploy`, `/retro`, `/investigate`, `/document-release`, `/codex`, `/cso`, `/autoplan`, `/careful`, `/freeze`, `/guard`, `/unfreeze`, `/gstack-upgrade`, `/learn`

## Skill routing

When the user's request matches an available skill, ALWAYS invoke it using the Skill
tool as your FIRST action. Do NOT answer directly, do NOT use other tools first.
The skill has specialized workflows that produce better results than ad-hoc answers.

Key routing rules:
- Product ideas, "is this worth building", brainstorming → invoke office-hours
- Bugs, errors, "why is this broken", 500 errors → invoke investigate
- Ship, deploy, push, create PR → invoke ship
- QA, test the site, find bugs → invoke qa
- Code review, check my diff → invoke review
- Update docs after shipping → invoke document-release
- Weekly retro → invoke retro
- Design system, brand → invoke design-consultation
- Visual audit, design polish → invoke design-review
- Architecture review → invoke plan-eng-review
