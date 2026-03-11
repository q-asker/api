# Development Guidelines

## Project Overview

- **Q-Asker** — AI 기반 퀴즈 생성·출제·채점 백엔드 API (Spring Boot 3.5.8 / Java 21)
- 베이스 패키지: `com.icc.qasker`
- 빌드 도구: Gradle 8.14.3 (Wrapper 사용, `./gradlew`)

## Project Architecture

### 멀티모듈 구조

```
app/                          ← 부트스트랩 (Spring Boot 메인)
modules/
  global/                     ← 전역 예외 처리, 공통 응답 DTO, BaseEntity
  auth/{api,impl}/            ← JWT, OAuth2 소셜 로그인
  aws/{api,impl}/             ← S3 파일 업로드/다운로드
  quiz/{api,impl}/            ← 퀴즈 CRUD, 출제·채점
  ai/{api,impl}/              ← Spring AI + Gemini 퀴즈 생성
  board/{api,impl}/           ← 게시판 (게시글, 댓글)
  util/{api,impl}/            ← 헬스체크 등 범용 기능
```

### 모듈 의존 방향 (반드시 준수)

```
app → 모든 impl + global
impl → 자신의 api + global + 다른 모듈의 api
api → (의존 없음 또는 global만)
```

- **금지**: `impl` → 다른 `impl` 직접 의존
- **금지**: `api` 모듈에 구현체 배치

## Code Standards

### 네이밍

| 대상        | 규칙                   | 예시                        |
|------------|----------------------|---------------------------|
| 클래스       | PascalCase           | `BoardService`, `PostRequest` |
| 메서드/변수   | camelCase            | `findById`, `boardTitle`   |
| 상수        | SCREAMING_SNAKE_CASE | `MAX_RETRY_COUNT`          |
| 패키지       | 소문자                 | `com.icc.qasker.board`     |
| 코드 주석    | 한국어                  | `// 게시글 삭제 처리`          |

### 클래스 구조

- 필드 → 생성자 → public 메서드 → private 메서드 순서
- `@RequiredArgsConstructor`로 생성자 주입 (**`@Autowired` 금지**)
- DTO: `record` 또는 `@Getter + @Builder`

### Import 순서

1. `java.*` / `javax.*`
2. `org.*` / `com.*` (서드파티)
3. `com.icc.qasker.*` (내부)
4. `static` import

## Functionality Implementation Standards

### 신규 기능 추가 절차

1. `modules/{도메인}/api/` — 인터페이스, DTO(record), 예외 클래스 작성
2. `modules/{도메인}/impl/` — 서비스 구현체, 리포지토리, 설정 클래스 작성
3. `app/` 부트스트랩은 수정하지 않음 (자동 빈 등록)
4. `./gradlew build` 실행하여 컴파일 오류 없음을 확인

### 신규 모듈 추가 절차

1. `modules/{도메인}/api/`, `modules/{도메인}/impl/` 디렉토리 생성
2. 각 모듈의 `build.gradle` 작성
3. `settings.gradle`에 모듈 등록
4. **`CLAUDE.md` 아키텍처 섹션 갱신**

### API 엔드포인트

- 경로: kebab-case (`/api/v1/quiz-sets`, `/api/v1/board-posts`)
- 응답: 공통 응답 DTO(`global` 모듈)로 래핑
- 예외: `global` 모듈 `GlobalExceptionHandler`에서 처리

### 환경 변수

- `application*.yml` + `@Value` 또는 `@ConfigurationProperties` 사용
- **코드에 하드코딩 금지**
- 신규 환경 변수 추가 시 **`CLAUDE.md` 환경 변수 섹션 갱신**

## Framework/Library Usage Standards

| 기술             | 사용 규칙                                              |
|----------------|------------------------------------------------------|
| Spring Data JPA | 리포지토리는 `impl` 모듈에만 위치                         |
| Lombok          | `@RequiredArgsConstructor`, `@Getter`, `@Builder`, `@Slf4j` 사용 |
| Spring AI       | `ai/impl`에만 Gemini 연동 코드 배치                      |
| Resilience4j    | 외부 API 호출(AI, AWS)에 CircuitBreaker/Retry 적용       |
| Spotless        | `./gradlew spotlessApply` — 커밋 전 자동 포맷팅 실행됨   |
| JWT             | `auth/impl`에만 발급·검증 로직 배치                       |

## Workflow Standards

### 작업 사이클 (Shrimp Task Manager)

```
plan_task → split_tasks → list_tasks → execute_task → (구현) → verify_task → reflect_task
```

### 커밋 전 체크리스트

1. `./gradlew build` 성공
2. `./gradlew spotlessCheck` 성공 (또는 spotlessApply 실행)
3. 의존성/모듈/환경 변수 변경 시 `CLAUDE.md` 갱신
4. `/commit` 스킬로 이모지 + 컨벤셔널 커밋 메시지 생성

## Key File Interaction Standards

| 변경 파일                          | 동시 수정 필수 파일                          |
|-----------------------------------|------------------------------------------|
| `build.gradle` (의존성 추가/삭제)   | `CLAUDE.md` 기술 스택 섹션                  |
| `settings.gradle` (모듈 추가)       | `CLAUDE.md` 아키텍처 섹션                   |
| `modules/*/` 디렉토리 생성/삭제     | `CLAUDE.md` 아키텍처 섹션                   |
| `application*.yml` 속성 추가        | `CLAUDE.md` 환경 변수/개발 도구 섹션          |
| `**/*Controller.java` 엔드포인트 변경 | `CLAUDE.md` (API 엔드포인트 섹션이 있는 경우) |
| Task 완료                           | `docs/roadmaps/ROADMAP_v*.md` 완료 표시    |

## AI Decision-making Standards

### 파일 위치 판단 트리

```
새 클래스 추가?
├── 인터페이스/DTO/예외 → {도메인}/api/src/main/java/com/icc/qasker/{도메인}/
│   ├── DTO → dto/request/ 또는 dto/response/
│   ├── 예외 → exception/
│   └── 인터페이스 → (루트 패키지)
└── 구현체/리포지토리/설정 → {도메인}/impl/src/main/java/com/icc/qasker/{도메인}/
    ├── 서비스 → service/
    ├── 컨트롤러 → controller/
    ├── 리포지토리 → repository/
    ├── 엔티티 → entity/
    ├── 설정 → config/
    └── 매퍼 → mapper/
```

### 모호한 상황 우선순위

1. `CLAUDE.md` 기술 스택 확인
2. 기존 동일 도메인 파일 구조 참조
3. 멀티모듈 api/impl 컨벤션 적용
4. 여전히 불명확하면 사용자에게 질문 (1~2개)

## Prohibited Actions

- **`@Autowired` 필드 주입** — `@RequiredArgsConstructor` 생성자 주입만 허용
- **`api` 모듈에 구현체(Service 구현, Repository) 배치**
- **`impl` → 다른 `impl` 직접 의존** (반드시 `api`를 통해)
- **환경 변수를 코드에 하드코딩**
- **`./gradlew build` 실패 상태로 커밋**
- **`CLAUDE.md` 미갱신 상태로 기술 스택/모듈/환경 변수 변경**
- **`.env`, `application-local.yml`, `application-prod.yml`을 Git 커밋**
- **요청 범위를 벗어난 파일 수정** (필요 시 별도 제안으로 분리)