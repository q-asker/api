# Q-Asker API

## 프로젝트 개요

문서(PDF, PPT, DOCX)를 업로드하면 Google Gemini AI가 자동으로 퀴즈를 생성하는 Spring Boot 기반 백엔드 API 서버. SSE를 통한 실시간 생성
스트리밍, 퀴즈 세트 관리, 풀이 히스토리 기록을 지원한다.

## 기술 스택

| 분류            | 기술                                                                    | 버전             |
|---------------|-----------------------------------------------------------------------|----------------|
| 언어            | Java                                                                  | 21             |
| 프레임워크         | Spring Boot                                                           | 4.1.0          |
| AI            | Spring AI (Google Gemini via Vertex AI)                               | 2.0.0          |
| ORM           | Spring Data JPA + Hibernate                                           | (Boot BOM)     |
| DB            | MySQL                                                                 | -              |
| 인증            | JWT (Auth0 java-jwt 4.5.0) + OAuth2 Client                            | -              |
| 클라우드          | OCI Java SDK (Object Storage) + Cloudflare CDN + Google Cloud Storage | 3.90.0         |
| 문서변환          | JODConverter (LibreOffice)                                            | 4.4.11         |
| PDF 처리        | Apache PDFBox                                                         | 3.0.7          |
| 모니터링          | Micrometer + Prometheus + Actuator                                    | (Boot BOM)     |
| 장애격리          | Resilience4j (Circuit Breaker)                                        | 2.4.0          |
| Rate Limiting | Bucket4j + Caffeine                                                   | 8.19.0         |
| API 문서        | SpringDoc OpenAPI (Swagger UI)                                        | 3.0.3          |
| 암호화           | Jasypt                                                                | 3.0.5          |
| ID 난독화        | Hashids                                                               | 1.0.3          |
| 빌드            | Gradle (Groovy DSL)                                                   | 8.14.3         |
| 컨테이너          | Jib (Docker)                                                          | 3.5.3          |
| 포맷터           | Spotless + Google Java Format                                         | 7.0.4 / 1.25.2 |
| DB 마이그레이션     | Flyway                                                                | (Boot BOM)     |
| 테스트           | JUnit 5                                                               | (Boot BOM)     |

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
│   ├── src/main/java/com/icc/qasker/loadtest/  # loadtest 전용 드라이버 (@Profile("loadtest")): LocalSchedulerController(비-controller 스케줄러 로직을 온디맨드 1회 호출해 백그라운드 쿼리 트레이스). read/write 부하는 loadgen.sh가 실 엔드포인트를 직접 태운다(mock 자기정리로 순증 0 — 별도 드라이버 불필요)
│   └── src/main/resources/
│       ├── application.yml       # 설정 진입점 (config/ import)
│       ├── application-secrets.yml  # 암호화된 시크릿
│       ├── application-test.yml  # test 프로파일 (CI/JUnit, H2 + 더미 Jasypt/OCI)
│       ├── db/migration/         # Flyway 마이그레이션 SQL (V1~V14)
│       └── config/               # 분리된 설정 파일들
│           ├── database-config.yml   # 서버, DB, JPA, 캐시
│           ├── ai-setting.yml        # Google Gemini AI 설정 (생성/ESSAY 채점/품질 검증 모델, 토큰 단가)
│           ├── spring-security.yml   # JWT, OAuth2, CORS
│           ├── oci-bucket-config.yml # OCI Object Storage, CDN
│           ├── jodconverter.yml      # LibreOffice 문서변환
│           ├── actuator.yml          # Actuator, Prometheus
│           ├── app-common.yml        # 앱 커스텀 설정
│           ├── github.yml            # 피드백 → GitHub 이슈 자동 등록 (owner/repo/토큰/라벨)
│           ├── resilience.yml        # Circuit Breaker
│           ├── spring-doc.yml        # Swagger/OpenAPI
│           └── loadtest.yml          # loadtest 프로파일 (분석 DB 3307 override, 레이트리밋 비활성)
├── modules/
│   ├── global/                   # 공통 (CreatedAt, CustomException/CustomErrorResponse, GlobalExceptionHandler, Boot4CompatConfig, RateLimitPlanResolver, HashUtil, SlackNotifier, GithubIssueClient, loadtest 쿼리 계측)
│   ├── auth/     (api + impl)    # 인증 (JWT, OAuth2, RateLimitFilter, JwtProvider, PrincipalExtractor, SecurityErrorResponder, TokenCrypto, LocalTokenController=@Profile("loadtest") 토큰 발급 헬퍼)
│   ├── oci/      (api + impl)    # OCI Object Storage 파일 업로드
│   ├── board/    (api + impl)    # 게시판
│   ├── quiz-ai/  (api + impl)    # AI 퀴즈 생성 (Gemini 호출, 메트릭, 품질 검증 QualityVerifier/QualityGate)
│   ├── quiz-make/(api + impl)    # 퀴즈 생성 흐름 (파일업로드, SSE, 생성결과)
│   ├── quiz-set/ (api + impl)    # 퀴즈 세트 CRUD, 품질 리뷰(QualityReviewService)·품질 로그(QualityLogService/ProblemQualityLog/QualityStatus, 재생성 원본 v1 포함)·스테일 생성 복구 스케줄러
│   ├── quiz-history/(api + impl) # 풀이 히스토리
│   ├── document/ (api + impl)    # 문서 변환 (PPT/DOCX → PDF)
│   └── admin/                    # 관리자 전용 API
├── infra/
│   ├── monitoring/               # Grafana Alloy 설정
│   ├── mysql/                    # MySQL Docker 설정
│   ├── base-image/               # Docker 베이스 이미지
│   ├── blue-green/               # Blue-Green 무중단 배포 (Nginx 트래픽 스위칭, docker-compose, deploy.sh)
│   └── scripts/
│       ├── oci-mysql-backup/     # OCI MySQL 백업/복구/헬스체크 스크립트 (backup.sh, restore.sh, healthcheck.sh, deploy.sh, env.example, healthcheck.baseline.yml, lib/, systemd/, RESTORE.md) — 리눅스·macOS 호환 (gzip -dc, sha256sum↔shasum 폴백)
│       ├── query-tuning/         # 쿼리 튜닝 부하 하네스 (스케일 스윕 x1/x10/x100 DB 대상, README.md에 실행 가이드): provision-level.sh(prod-matched config로 127.0.0.1 레벨 컨테이너 생성), loadgen.sh(실 엔드포인트 타격 단일 레시피 — 읽기 GET·실 write[mock 순증 0]·스케줄러·refresh·SSE 생성구독·로그아웃; admin·/local·/upload-doc[외부IO]·/auth/test 외 전 엔드포인트 요청), run-level.sh(레벨별 실행 — 무거운 패스=§① Micrometer seed + 가벼운 패스=§②③ trace_snapshot 귀속 + slow_log 수집), run-all.sh(3레벨 순차 스윕 오케스트레이터 — 레벨→포트→컨테이너 매핑 내장, ROUNDS 등 env 통과)
│       └── perf-seed/             # 스케일 시딩 (x1 원본 복원본에 배수 시드 추가, FK 정합 유지): seed-scale.sh(작은 테이블 일괄 + problem 배치 시딩·총량 검증), seed-scale-small.sql, seed-scale-problem.sql
├── docs/                         # 문서, 분석 자료
├── gradle/
│   ├── libs.versions.toml        # Version Catalog: 모든 의존성/플러그인 버전 SSOT
│   └── wrapper/                  # Gradle Wrapper
├── .githooks/                    # Git 훅 (pre-commit, pre-push, prepare-commit-msg)
└── .github/workflows/            # CI/CD
    ├── cd-prod_deploy.yml
    ├── ci-auto-version-bump.yml
    ├── ci-check-code-convention.yml
    ├── ci-update-api-docs.yml
    ├── renovate-impact-analysis.yml
    └── feedback-review.yml       # 피드백 이슈 /review 시 Claude가 FE/BE 작업 분석, @claude 멘션 시 후속 Q&A
```

## 환경 변수

- 민감한 값은 `application-secrets.yml`에 Jasypt `ENC()`로 암호화하여 관리
- Jasypt 복호화 키: `JASYPT_PASSWORD` 환경변수 또는 JVM 옵션으로 전달
- 프로파일: `local` (개발), `prod` (운영), `test` (CI/JUnit), `loadtest` (부하 테스트, `local`에 얹어 실행 — `SPRING_PROFILES_ACTIVE=local,loadtest`), `mock` (AI 외부 호출 우회, 선택적으로 얹어 실행 — MockAIServerAdapter·MockEssayGradingService가 `@Profile("mock")`으로 Gemini 호출 없이 고정 결과 반환)
- Actuator 포트: 9090 (서비스 포트와 분리)
- Virtual Threads 활성화 (`spring.threads.virtual.enabled: true`)
- OCI Object Storage: `~/.oci/config` 파일 기반 인증, `OCI_NAMESPACE`, `OCI_IMAGE_BUCKET_NAME`,
  `OCI_PDF_BUCKET_NAME` 환경변수
- Google Cloud: Vertex AI + GCS (ADC 인증)
    - `spring.ai.google.genai.project-id`: GCP 프로젝트 ID
    - `spring.ai.google.genai.location`: GCP 엔드포인트 (현재: `global`)
    - `q-asker.ai.gcs.bucket-name`: GCS 버킷 이름 (application-secrets.yml에 ENC로 주입)
    - 로컬: `gcloud auth application-default login`, 프로덕션: 서비스 계정
- DDoS 방어: Cloudflare Free (`api.q-asker.com`만 프록시 활성화)
- SSL/HTTPS: Cloudflare (Universal SSL) → Nginx (Origin CA TLS), Full (Strict) 모드
    - Origin 인증서: Cloudflare Origin CA (15년 유효)
    - 인증서 경로: `/etc/ssl/cloudflare/api.q-asker.com.pem`, `.key`
    - OCI NSG: 80/443 인바운드 Cloudflare IP 대역만 허용

## 개발 도구 및 설정

- **빌드**: Gradle 8.14.3 (Groovy DSL)
- **JDK**: 21 (Gradle Toolchain 자동 관리)
- **Version Catalog**: `gradle/libs.versions.toml`이 모든 의존성·플러그인 버전의 SSOT
    - 모듈 build.gradle에서는 `libs.xxx` 참조로 사용
    - 새 의존성/버전 변경은 반드시 catalog에서 시작
    - `settings.gradle`의 foojay 플러그인도 catalog TOML을 직접 파싱하여 일관성 유지
- **Dependency Locking**: 모든 모듈에 `gradle.lockfile` 적용, transitive 의존성까지 박제
    - `./gradlew resolveAndLockAll --write-locks` — lockfile 일괄 재생성
    - `compileJava` 등 빌드 시 자동 검증, drift 발생 시 빌드 실패
    - pre-commit hook이 의존성 파일 변경 감지 시 자동 재생성·staging
- **Renovate**: `renovate.json`으로 의존성 업데이트 PR 자동 생성 (월요일 오전 KST 스케줄, 그룹핑 적용)
- **포맷터**: Spotless + Google Java Format 1.25.2
    - `./gradlew spotlessApply` — 포맷 적용
    - `./gradlew spotlessCheck` — 포맷 검증
- **Git Hooks** (`.githooks/`)
    - `prepare-commit-msg` — 브랜치에서 JIRA 티켓(`[A-Z]+-[0-9]+`) 감지하여 커밋 메시지 접두사 자동 추가
    - `pre-commit` — `spotlessCheck` + 의존성 lockfile 자동 동기화(`build.gradle`/`settings.gradle`/`libs.versions.toml` 변경 감지 시 `resolveAndLockAll` 실행 후 `gradle.lockfile` 자동 staging) + `application-secrets.yml` 암호화 검증
    - `pre-push` — `spotlessCheck` 실행, 위반 시 푸시 차단
- **CI/CD**: GitHub Actions
    - `ci-check-code-convention.yml` — PR 포맷 검증
    - `ci-auto-version-bump.yml` — 자동 버전 범프
    - `ci-update-api-docs.yml` — OpenAPI 스펙 자동 갱신
    - `renovate-impact-analysis.yml` — Renovate PR 영향 분석
    - `feedback-review.yml` — 피드백 이슈 `/review` 시 Claude 프론트/백엔드 작업 분석, `@claude` 멘션 시 후속 Q&A(맥락 보강)
    - `cd-prod_deploy.yml` — 운영 배포

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
`specs/002-quiz-quality-gate/plan.md` (research.md, data-model.md,
contracts/, quickstart.md in the same directory)
<!-- SPECKIT END -->
