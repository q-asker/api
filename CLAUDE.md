# Q-Asker API

## 프로젝트 개요

문서(PDF, PPT, DOCX)를 업로드하면 Google Gemini AI가 자동으로 퀴즈를 생성하는 Spring Boot 기반 백엔드 API 서버. SSE를 통한 실시간 생성
스트리밍, 퀴즈 세트 관리, 풀이 히스토리 기록을 지원한다.

## 기술 스택

| 분류            | 기술                                                                    | 버전             |
|---------------|-----------------------------------------------------------------------|----------------|
| 언어            | Java                                                                  | 25             |
| 프레임워크         | Spring Boot                                                           | 4.1.0          |
| AI            | Spring AI (Google Gemini via Vertex AI)                               | 2.0.0          |
| ORM           | Spring Data JPA + Hibernate                                           | 7.4.5.Final    |
| DB            | MySQL                                                                 | -              |
| 인증            | JWT (Auth0 java-jwt 4.6.0) + OAuth2 Client                            | -              |
| 클라우드          | OCI Java SDK (Object Storage) + Cloudflare CDN + Google Cloud Storage | 3.90.0         |
| 문서변환          | JODConverter (LibreOffice)                                            | 4.4.11         |
| PDF 처리        | Apache PDFBox                                                         | 3.0.8          |
| 모니터링          | Micrometer + Prometheus + Actuator                                    | (Boot BOM)     |
| 장애격리          | Resilience4j (Circuit Breaker)                                        | 2.4.0          |
| Rate Limiting | Bucket4j + Caffeine                                                   | 8.19.0         |
| API 문서        | SpringDoc OpenAPI (Swagger UI)                                        | 3.0.3          |
| 암호화           | Jasypt                                                                | 3.0.5          |
| ID 난독화        | Hashids                                                               | 1.0.3          |
| 빌드            | Gradle (Groovy DSL)                                                   | 8.14.5         |
| 컨테이너          | Jib (Docker)                                                          | 3.5.3          |
| 포맷터           | Spotless + Google Java Format                                         | 8.8.0 / 1.25.2 |
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
│   ├── src/main/java/com/icc/qasker/loadtest/  # 측정 드라이버 (패키지명은 loadtest이나 전부 @Profile("local") — loadtest는 local에 얹혀 실행되므로 부하 시에도 활성): LocalSchedulerController(타이머 @Profile("!local")로 꺼진 스케줄러 로직을 온디맨드 1회 호출해 백그라운드 쿼리 트레이스), RequestResourceMetricsFilter/Config(요청 스레드의 CPU 시간·힙 할당 바이트 델타를 uri별 카운터 request.thread.cpu.seconds·request.thread.allocated.bytes로 누적 — 플랫폼 스레드+동기 MVC 전제, 가상 스레드에선 무효), JfrMethodTimingExporter(JEP 520 jdk.MethodTiming 이벤트를 JFR 스트리밍으로 구독해 게이지 jfr.method.invocations·jfr.method.time.total로 노출 — run.sh MT_FILTER 6종과 동기 유지, qasker-enh-rw가 mode 라벨로 비교). read/write 부하는 run.sh 내장 loadgen 함수가 실 엔드포인트를 직접 태운다(mock 자기정리로 순증 0 — 별도 드라이버 불필요)
│   ├── src/main/java/com/icc/qasker/dev/  # 로컬 전용(@Profile("local")) 벤치 훅 (ExplanationReviewBenchController)
│   └── src/main/resources/
│       ├── application.yml       # 설정 진입점 (config/ import)
│       ├── application-secrets.yml  # 암호화된 시크릿
│       ├── application-test.yml  # test 프로파일 (CI/JUnit, H2 + 더미 Jasypt/OCI)
│       ├── db/migration/         # Flyway 마이그레이션 SQL (V1~V19)
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
│           └── mock.yml              # mock 프로파일 (구 loadtest 흡수: 분석 DB 3309 override, 레이트리밋 비활성)
├── modules/
│   ├── global/                   # 공통 (CreatedAt, CustomException/CustomErrorResponse, GlobalExceptionHandler, Boot4CompatConfig, RateLimitPlanResolver, HashUtil, SlackNotifier, GithubIssueClient, 로컬 쿼리 계측(@Profile("local")))
│   ├── auth/     (api + impl)    # 인증 (JWT, OAuth2, RateLimitFilter, JwtProvider, PrincipalExtractor, SecurityErrorResponder, TokenCrypto, LocalTokenController=@Profile("local") 토큰 발급 헬퍼)
│   ├── oci/      (api + impl)    # OCI Object Storage 파일 업로드
│   ├── board/    (api + impl)    # 게시판
│   ├── quiz-ai/  (api + impl)    # AI 퀴즈 생성 (Gemini 호출, 청크 분할 스트리밍(ChunkPlanner/AbstractChunkedQuizOrchestrator)·컨텍스트 캐시(GeminiContextCacheManager), 메트릭, 품질 검증 QualityVerifier/QualityGate)
│   ├── quiz-make/(api + impl)    # 퀴즈 생성 흐름 (파일업로드, SSE, 생성결과)
│   ├── quiz-set/ (api + impl)    # 퀴즈 세트 CRUD, 품질 리뷰(QualityReviewService, problemSetIds 배치 재검토)·해설 재검토(ExplanationReviewService/ExplanationFormatValidator)·품질 로그(QualityLogService/ProblemQualityLog, v1 생성본·v2 재생성본 함께 보관)·스테일 생성 복구 스케줄러(FAILED·10분 초과 GENERATING 세트를 ProblemSet 애그리거트로 삭제 — 자식 problem 은 FK ON DELETE CASCADE[V19]가 DB 에서 자동 삭제, mock 은 flush 후 롤백으로 순증 0)
│   ├── quiz-history/(api + impl) # 풀이 히스토리 + 기록 폴더 분류(QuizFolder 엔티티, /folders CRUD[POST·GET·PATCH·DELETE], PATCH /history/{id}/folder 배정·해제, GET /history?scope=ALL|UNCLASSIFIED|FOLDER&folderId= 필터링; QuizFolderCommand/QueryService, QuizHistory.folder_id)
│   ├── document/ (api + impl)    # 문서 변환 (PPT/DOCX → PDF)
│   └── admin/                    # 관리자 전용 API
├── infra/
│   ├── monitoring/               # Grafana Alloy 설정
│   ├── mysql/                    # MySQL Docker 설정
│   ├── base-image/               # Docker 베이스 이미지
│   └── blue-green/               # Blue-Green 무중단 배포 (Nginx 트래픽 스위칭, docker-compose, deploy.sh)
├── scripts/                      # 로컬 측정 하네스 (부하 스윕·A/B, api 루트 기준 실행)
│   ├── query-tuning/         # 쿼리 튜닝 부하 하네스 (스케일 스윕 x1/x10/x100 DB 대상, README.md에 실행 가이드): provision-level.sh(prod-matched config로 127.0.0.1 레벨 컨테이너 생성), run.sh(3레벨 순차 스윕 오케스트레이터 — 내장 () 서브셸 함수 loadgen()[실 엔드포인트 타격 단일 레시피, 균일 모델=가상 유저(vu_loadtest) 1명이 각 엔드포인트를 CONC(기본 10) 동시 × ROUNDS 라운드 반복, 페이로드는 x1 실측 avg 크기 고정 문자열: 읽기 GET·실 write(mock 순증 0)·스케줄러·refresh·SSE 생성구독·로그아웃 + admin 패스(별도 ROLE_ADMIN 토큰으로 quality-review GET·POST·explanation-review POST[실 서비스, 마킹은 동일값 재대입이라 순증 0]·admin 게시판 write); /upload-doc(외부IO)·/auth/test 외 전 엔드포인트; USER_ID 는 run_level 이 seed-vuser.sql 로 가상 유저를 합성해 주입, ADMIN_USER_ID·ADMIN_SET_IDS 는 대상 DB 에서 자동 조회]·run_level()[레벨별 실행: 단일 패스로 §① Micrometer seed 와 §②③ trace_snapshot 귀속을 함께 수집(링버퍼 10만행이 부하 전체를 담아 옛 무거운/가벼운 2패스 폐기, 포화 시 경고) + slow_log 수집]; 레벨→포트→컨테이너 매핑 내장, 레벨 실행 전 다른 레벨 컨테이너 정지[RAM 압박 JVM 강제종료 방지], ROUNDS 등 env 통과, `run.sh <레벨>`로 단일 레벨 실행=옛 loadgen.sh·run-level.sh 를 인라인 통합), download-masked.sh(OCI 마스킹 덤프 다운로드+sha256 검증 → 파일 경로 출력), restore-x1.sh(마스킹 덤프 파일을 local-mysql-x1 복원 + row·FK 정합 검증 + x1 베이스 top-up + 성공 시 masked 덤프 삭제[로컬 파일+버킷 객체], 다운로드와 독립), seed-x1-base.sql(FLOOR 미달 소형 테이블 board·feedback_board·quiz_folder·reply 를 x1 에서 100행으로 맞춤 — 부족분만 user·board FK 재사용해 합성, 오프셋 <@base 라 ×scale 복제 대상에 포함 → x100 에서 10,000), seed-vuser.sql(부하용 가상 유저 vu_loadtest 1명+소유 리소스 @k개를 기존 실 행 재소유 복사로 합성 — DELETE→재생성이라 재실행 결정적, run.sh run_level 이 레벨마다 실행), seed-stale-generation.sql(방치 세트 정리 측정용 스테일 시드 — 전체 COMPLETED 리셋 후 최저 id 24건을 FAILED·2시간 전으로 고정[실측 유도: 400 RPM×실패율 6%×스케줄러 주기 1분=24, 정상상태 개수가 DB 크기와 무관하므로 비율 아닌 고정 개수 — 비율 시딩은 x100 에서 cascade 삭제 락 타임아웃], run_level 이 매 레벨 부하 직전 재적용해 레벨마다 동일 분포·idempotent), seed-scale.sh + seed-scale.sql(스케일 시딩 — 무인자면 x10·x100 순차 시딩; 대상 볼륨 fresh 재생성[provision-level, 시리얼·파일 누적 리셋] → x1 복원 → 프로시저 seed_scale 로 전 테이블 배수 복제[소형=CROSS JOIN 한 방, problem=copy WHILE 루프 청킹; quiz_folder·reply·problem_quality_log 포함; FK 정합은 같은 copy 참조 오프셋으로 구성상 보장] → 총량(원본×배수) 검증; 내장 check_x1_scale 로 x<배수> 투영이 FLOOR 미만인 도메인 테이블 경고=옛 check-x1-scale.sh 인라인 통합)
│   └── hibernate-enhancement/ # Hibernate 바이트코드 인핸스먼트 A/B 측정 하네스 (run.sh [off|on] — 무인자면 off→on 쌍을 PAIRS회(기본 5) 반복해 런 간 노이즈 평균화, Grafana qasker-enh-rw가 mode·run 라벨로 자동 비교): quiz-set-impl을 `-PdisableHibernateEnhancement` OFF / 무플래그 ON 두 빌드로 clean 재빌드→javap로 계측 적용 검증→local,mock 기동(query-tuning 스케일 DB 연결, 기본 x100=3309·정지 상태면 자동 기동)→problem_quality_log 리플레이 시딩(합성 세트 DELETE→INSERT 로 매 실행 동일 초기 상태, x100 실측 크기 분포 — 대형 질문 JSON + 세트당 일부 망가진 해설=마킹 쓰기)→admin 토큰 자동 발급→POST /admin/problem-sets/explanation-review 반복 부하(마킹 쓰기의 더티체크 경로 정조준)→MySQL 문장 단위 귀속(SQL 주석 reqId·mode 기반 performance_schema 집계를 영구 테이블 enh_snapshot 에 적재 — Grafana MySQL 데이터소스 직조회)→구간 끝 epoch 출력(기록용)→JFR 덤프 집계(jdk.MethodTiming 확정 필터 6종=순수 CPU 5종[더티체크 4+조립 창 readRow]+혼합 창 executeQuery 1종[대기 포함 wall이라 CPU 주장 금지, 지연 분해용] — 창 밖 혼입되는 소켓 read류는 제외; 실행 중엔 JfrMethodTimingExporter가 같은 필터를 게이지로 실시간 노출). 계측(dirty tracking·지연 로딩)은 기본 ON·quiz-set-impl에만 적용(`-PdisableHibernateEnhancement`로 OFF; build.gradle §Hibernate 인핸스먼트 참고)
├── docs/                         # 문서, 분석 자료
├── gradle/
│   ├── libs.versions.toml        # Version Catalog: 모든 의존성/플러그인 버전 SSOT
│   └── wrapper/                  # Gradle Wrapper
├── .githooks/                    # Git 훅 (pre-commit, pre-push, prepare-commit-msg)
└── .github/workflows/            # CI/CD
    ├── cd-prod_deploy.yml
    ├── ci-auto-version-bump.yml
    ├── ci-check-code-convention.yml
    ├── ci-pii-coverage.yml
    ├── ci-update-api-docs.yml
    └── renovate-impact-analysis.yml
```

## 인증 / JWT (작업 가이드)

JWT 관련 작업 시 아래 위치만 보면 된다. 서명·검증 로직을 여기저기서 재구현하지 말 것 — **단일 진입점은 `JwtProvider`**.

- **서명·검증 단일 진입점**: `modules/auth/impl/.../component/JwtProvider.java`
  - 알고리즘 **HMAC512**. `sign(User)` → 액세스 토큰. 클레임: `subject`·`userId`(둘 다 userId), `nickname`, `role`, `exp`.
  - `verifyAndExtractUserId(token)` → 성공 시 userId, **실패(만료·위조·손상)는 모두 `null`로 흡수**(익명 통과 정책). 예외를 던지지 않는다.
  - 알고리즘·시크릿·클레임을 바꿔야 하면 **이 클래스만** 수정한다.
- **설정(시크릿·만료)**: `JwtProperties`(`modules/global/.../properties/JwtProperties.java`, `@ConfigurationProperties("spring.security.jwt")`) — `secret`, `accessExpirationSecond`, `refreshExpirationSecond`. 값은 `app/src/main/resources/config/spring-security.yml`(secret은 Jasypt `ENC()`).
- **요청 인증 필터**: `modules/auth/impl/.../config/security/filter/JwtTokenAuthenticationFilter.java` — 헤더 토큰을 검증해 SecurityContext에 인증을 심는다. 검증 실패는 익명으로 통과(위 정책).
- **컨트롤러에서 userId 받기**: 파라미터에 `@UserId String userId`(`modules/global/.../annotation/UserId.java`) — `UserIdArgumentResolver`(`modules/auth/impl/.../util/`)가 SecurityContext에서 주입. 컨트롤러가 직접 토큰을 파싱하지 않는다.
- **리프레시·회전**: `TokenRotationService`(api) + `RefreshToken` 엔티티/`RefreshTokenRepository`, 암호화는 `TokenCrypto`/`RefreshTokenUtil`.
- **로컬/부하 토큰 발급 (OAuth 우회)**: `LocalTokenController` → `GET /local/token?userId=<기존 User id>` 가 `jwtProvider.sign` 결과(액세스 토큰 문자열)를 반환. **`@Profile("local")` 에서만 등록되고 `prod` 에는 절대 노출되지 않는다**(부하 하네스는 항상 `local`에 얹어 실행되므로 부하 테스트에서도 활성). 인증이 필요한 API를 로그인 없이 태울 때(로컬 개발·**E2E**·부하 테스트) 사용. userId는 DB에 이미 존재해야 함(없으면 404).

## 환경 변수

- 민감한 값은 `application-secrets.yml`에 Jasypt `ENC()`로 암호화하여 관리
- Jasypt 복호화 키: `JASYPT_ENCRYPTOR_PASSWORD` 환경변수 또는 JVM 옵션으로 전달
  - **로컬에서는 이 값을 찾아 헤매지 말 것** — `app/gradle.properties` 의 `JASYPT_ENCRYPTOR_PASSWORD` 에 이미 있고,
    `app/build.gradle`(run/test 태스크)이 이 프로퍼티를 읽어 실행 환경변수로 자동 주입한다. 즉 `./gradlew :app:bootRun`(또는 `:app:test`)은 별도 export 없이 복호화된다. 셸에 직접 export하거나 secret 저장소를 뒤질 필요 없다.
- 프로파일: `local` (개발), `prod` (운영), `test` (CI/JUnit), `mock` (**부하 테스트 + 외부 호출·실쓰기 우회를 통일** — `local`에 얹어 실행, `SPRING_PROFILES_ACTIVE=local,mock`. `mock.yml`이 분석 DB 3309·레이트리밋 off를 담당[구 loadtest 흡수]하고, MockAIServerAdapter·MockEssayGradingService가 `@Profile("mock")`으로 Gemini 호출 없이 고정 결과 반환 — 생성(`POST /generation`)은 MockGenerationCommandService(`quiz-make/.../service/generation/`, `@Primary` — 실 구현과 달리 요청 스레드 동기 실행으로 부하 계측 귀속·E2E 응답 시점 저장 완료 보장)가 MockAIServerAdapter의 결정론적 픽스처(1번 문항은 마크다운 대표 픽스처)로 SSE·저장 포함 흐름을 태워 문제 세트가 실제 저장된다(순증 0 아님, E2E 시드 겸용). 도메인별 write mock(board·feedback·user·history·folder·problem-set·quality-review 등 `service/mock/Mock*Service`)이 write를 자기정리(save→delete 또는 롤백)로 순증 0으로 태움 — loadgen의 실 write 계측 전제)
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

- **빌드**: Gradle 8.14.5 (Groovy DSL)
- **JDK**: 25 (Gradle Toolchain 자동 관리, 런타임 eclipse-temurin:25 `infra/base-image`)
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
    - `ci-pii-coverage.yml` — 마이그레이션 PR에서 스키마 컬럼을 `pii_classification`에 전부 분류했는지 게이트(미분류 시 실패, 마스킹 export deny-by-default 사전 차단)
    - `renovate-impact-analysis.yml` — Renovate PR 영향 분석
    - `cd-prod_deploy.yml` — 운영 배포

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
`specs/002-quiz-quality-gate/plan.md` (research.md, data-model.md,
contracts/, quickstart.md in the same directory)
<!-- SPECKIT END -->
