# /sync-claude — .claude/ 디렉토리 동기화

## 역할

`.claude/` 디렉토리 전체를 현재 프로젝트에 맞게 동기화한다.
다른 프로젝트에서 `.claude/`를 복사해 온 경우 **재조정**하고, 이미 현재 프로젝트용이면 **일관성 검증 + 보완**한다.

**동기화 대상:**

| 대상 | 설명 |
|---|---|
| `rules/` | 코딩 컨벤션, 제약사항, 작업 흐름 등 |
| `commands/` | 슬래시 커맨드 |
| `agents/` | 에이전트 설정 |
| `hooks/` | 훅 스크립트 |
| `scripts/` | 유틸리티 스크립트 |
| `settings.json` | 공유 설정 (훅, 플러그인) |
| `init-context.json` | 프로젝트 메타데이터 |

**제외 대상:** `.env`, `settings.local.json`, `.DS_Store`

---

## STEP 1 — 컨텍스트 수집

1. `CLAUDE.md` 읽기 → 기술 스택, 프레임워크, 아키텍처 파악
2. `CLAUDE.md` 없으면 `/init` 먼저 실행 안내 후 **중단**
3. `.claude/` 디렉토리 전체 스캔 → 각 하위 항목의 존재 여부와 내용 파악
4. `init-context.json` 읽기 → 기존 메타데이터 확인

### 1-1. 일치성 판정

**각 동기화 대상**의 내용을 읽고, CLAUDE.md의 기술 스택 키워드(`language`, `framework`, 빌드 도구 등)와 대조하여 판정한다:

| 판정 | 조건 | 동작 |
|---|---|---|
| **ok** | 내용이 현재 프로젝트와 일치 | 누락 항목만 보완 (기존 내용 보존) |
| **mismatch** | 다른 프로젝트의 내용이 섞여 있음 | 아래 재작성 규칙에 따라 처리 |
| **missing** | 파일/디렉토리가 없음 | 신규 생성 |

### 1-2. mismatch 재작성 규칙

mismatch 판정된 파일은 **용도에 따라** 재작성 범위를 달리한다:

| 유형 | 판단 기준 | 재작성 방식 |
|---|---|---|
| **프로젝트 전용** | `rules/`, 프로젝트별 설정 | 현재 프로젝트 기술 스택에 맞게 전면 재작성 |
| **범용 도구** | `agents/`, `commands/` 중 여러 프로젝트에서 공유하는 파일 | 기술 스택에 종속된 부분만 중립화하거나 현재 프로젝트에 맞게 수정. 사용자에게 "프로젝트 전용 vs 범용 유지" 확인 후 처리 |

---

## STEP 2 — 동기화 실행

아래 항목을 순서대로 처리한다. 각 항목은 STEP 1의 판정 결과에 따라 **보완** 또는 **재작성**한다.

### 2-1. `rules/` — 규칙 파일 동기화

아래 5개 파일을 프로젝트 기술 스택에 맞게 생성/보완한다.

#### `task-workflow.md`

```markdown
# 작업 흐름 규칙

## 세션 시작 가드 (최우선)

**모든 작업보다 먼저** 아래 2가지를 확인한다. 실패 시 작업을 시작하지 않는다.

1. `CLAUDE.md`에 `⚠️ 초기화 미완료` 마커가 있는지 확인
    - 있으면 → 사용자에게 안내: `⚠️ 프로젝트 초기화가 완료되지 않았습니다. /sync-claude를 먼저 실행하세요.`
    - 사용자가 명시적으로 "무시하고 진행해"라고 하지 않는 한 작업을 시작하지 않는다
2. `CLAUDE.md`가 없으면 → 사용자에게 안내: `⚠️ CLAUDE.md가 없습니다. /init을 먼저 실행하세요.`

## 작업 모드

| 모드         | 트리거                              | 동작                    |
| ------------ | ----------------------------------- | ----------------------- |
| 즉시 실행    | 요청이 명확하고 범위가 작을 때      | 바로 구현               |
| 계획 후 실행 | 범위가 크거나 아키텍처 변경 포함 시 | 계획 공유 → 승인 → 구현 |
| 질문 우선    | 요구사항이 모호할 때                | 핵심 질문 1~2개 → 구현  |

## 모호한 상황 대처

- 기술적 선택이 필요한 경우 `CLAUDE.md`의 기술 스택을 우선 따른다
- 파일 경로가 불명확하면 기존 컨벤션을 따른다
- 테스트 방식이 불명확하면 기존 테스트 파일 패턴을 따른다

## 질문 원칙

- 한 번에 최대 2개 질문
- 구현에 직접 영향을 주는 것만 질문
- 추론 가능한 것은 질문하지 않고 결정 후 명시
```

#### `coding-conventions.md`

기술 스택에 따라 분기하여 작성한다.

**TypeScript / JavaScript 계열:**

```markdown
# 코딩 컨벤션

## 네이밍

- 컴포넌트: PascalCase
- 함수/변수: camelCase
- 상수: SCREAMING_SNAKE_CASE
- 파일: kebab-case (컴포넌트는 PascalCase)

## 컴포넌트

- 함수형 컴포넌트만 사용
- Props 타입은 interface로 정의, 파일 상단에 배치
- default export 사용

## 스타일링

- TODO: 프로젝트 스타일링 방식 명시 (Tailwind / CSS Modules / styled-components)

## Import 순서

1. 외부 라이브러리
2. 내부 모듈 (절대경로)
3. 상대경로
4. 타입 import (type 키워드 사용)

## 기타

- `any` 타입 사용 금지
- 비동기는 async/await 사용 (Promise chain 금지)
```

**Java 계열:**

```markdown
# 코딩 컨벤션

## 네이밍

- 클래스명: PascalCase
- 메서드/변수: camelCase
- 상수: SCREAMING_SNAKE_CASE
- 패키지: 소문자
- 파일명: 클래스명과 동일

## 클래스 구조

- 하나의 파일에 하나의 public 클래스
- 필드 → 생성자 → public 메서드 → private 메서드 순서
- Lombok `@RequiredArgsConstructor`로 생성자 주입 (필드 주입 `@Autowired` 금지)
- DTO는 `record` 또는 Lombok `@Getter` + `@Builder` 사용

## Import 순서

1. `java.*` / `javax.*` (표준 라이브러리)
2. `org.*` / `com.*` (서드파티)
3. 프로젝트 내부 패키지
4. `static` import

## 기타

- 비동기: `@Async` + `CompletableFuture` 사용
- 로깅: `@Slf4j` (Lombok) 사용
```

**Python 계열:**

```markdown
# 코딩 컨벤션

## 네이밍

- 클래스: PascalCase
- 함수/변수: snake_case
- 상수: SCREAMING_SNAKE_CASE
- 파일: snake_case

## 타입

- 모든 함수에 타입 힌트 필수
- `Any` 타입 사용 금지

## Import 순서 (isort 기준)

1. 표준 라이브러리
2. 서드파티
3. 내부 모듈

## 기타

- Docstring: Google 스타일
- 최대 줄 길이: 88자 (black 기본값)
```

#### `constraints.md`

```markdown
# 제약 사항

## 절대 금지

- CLAUDE.md 수정 없이 기술 스택 변경
- 환경 변수를 코드에 하드코딩
- TODO: 프로젝트 특성에 맞는 금지 항목 추가

## 주의 사항

- 기존 API 인터페이스 변경 시 반드시 사전 공유
- 외부 라이브러리 추가 시 CLAUDE.md 기술 스택 섹션 갱신
- TODO: 프로젝트 특성에 맞는 주의 항목 추가

## 파일 수정 범위

- 요청 범위를 벗어난 파일은 수정하지 않는다
- 리팩토링이 필요하면 별도 제안으로 분리한다
```

#### `document-sync.md`

```markdown
# 문서 연동 규칙

## 갱신 트리거

| 변경 사항           | 갱신 대상                |
| ------------------- | ------------------------ |
| 기능 추가/변경      | CLAUDE.md 아키텍처 섹션  |
| API 엔드포인트 변경 | docs/PRD.md (존재 시)    |
| 환경 변수 추가      | CLAUDE.md 환경 변수 섹션 |
| 패키지 추가/제거    | CLAUDE.md 기술 스택 섹션 |
| 디렉토리 구조 변경  | CLAUDE.md 아키텍처 섹션  |

## 문서 우선순위

1. `CLAUDE.md` — 항상 최신 상태 유지
2. `docs/PRD.md` — 기능 명세 변경 시 갱신
3. `docs/roadmaps/ROADMAP_v*.md` — 마일스톤 변경 시 갱신

## 규칙

- 코드 변경과 문서 갱신은 **같은 커밋**에 포함한다
- 문서만 수정하는 커밋은 `docs:` prefix 사용
```

#### `agent-delegation.md` (선택적)

프로젝트에서 사용하는 에이전트/스킬이 있을 때만 생성한다. `.claude/agents/`와 사용 가능한 스킬 목록을 기반으로 작성한다.

```markdown
# 에이전트 위임 규칙

## 사용 가능한 에이전트/스킬

<!-- .claude/agents/ 스캔 결과와 스킬 목록을 반영 -->

## 위임 원칙

- 스킬이 존재하는 작업은 반드시 해당 스킬을 사용한다
- 스킬 없이 처리 가능한 작업은 직접 수행한다
- 스킬 실행 실패 시 사용자에게 알리고 대안을 제시한다

## 스킬별 트리거

| 스킬 | 트리거 조건 |
|---|---|
| TODO: 스킬명 | TODO: 트리거 조건 |
```

---

### 2-2. `commands/` — 커맨드 파일 검증

각 커맨드 파일을 읽고 아래를 확인한다:

1. **프로젝트 참조 일관성** — 커맨드 내 언급된 기술 스택, 경로, 도구가 CLAUDE.md와 일치하는지
2. **상호 참조 정합성** — 커맨드 간 참조 (예: `/init` → `/sync-claude` → `/plan-feature` 워크플로우)가 올바른지
3. **참조 파일 존재 여부** — 커맨드가 `@파일경로`나 파일명으로 참조하는 파일이 실제로 존재하는지 확인. 존재하지 않는 파일을 참조하면 해당 참조를 제거하거나 주석 처리한다
4. **불일치 발견 시** — 해당 부분을 수정하고 변경 내역을 리포트에 포함

---

### 2-3. `agents/` — 에이전트 설정 검증

각 에이전트 파일의 **전체 내용**(description, 본문, 템플릿, 가이드라인, 예시 코드 포함)을 읽고 아래를 확인한다:

1. **기술 스택 참조 (전체 스캔)** — 에이전트의 description뿐 아니라 **본문의 템플릿, 가이드라인, 체크리스트, 출력 예시** 등에서 다른 프로젝트의 기술 스택을 참조하는지 확인한다. 특히 하드코딩된 프레임워크명, 버전, 도구명에 주의한다
2. **불필요한 에이전트** — 현재 프로젝트에서 사용하지 않는 에이전트가 있는지 (제거하지 않고 리포트에 보고)
3. **누락된 에이전트** — 프로젝트 특성상 필요하지만 없는 에이전트가 있는지 (리포트에 제안)
4. **mismatch 처리** — 기술 스택 불일치 발견 시 1-2절의 재작성 규칙(프로젝트 전용 vs 범용)에 따라 처리한다

---

### 2-4. `hooks/` — 훅 스크립트 검증

각 훅 스크립트를 읽고 아래를 확인한다:

1. **실행 권한** — 스크립트에 실행 권한(`chmod +x`)이 있는지
2. **의존성** — 스크립트가 참조하는 외부 도구/경로가 유효한지
3. **settings.json 연결** — `settings.json`의 hooks 설정과 실제 스크립트 파일이 매칭되는지

---

### 2-5. `scripts/` — 유틸리티 스크립트 검증

각 스크립트를 읽고 아래를 확인한다:

1. **실행 권한** — 스크립트에 실행 권한이 있는지
2. **프로젝트 적합성** — 스크립트 내 하드코딩된 경로/도구가 현재 프로젝트에 맞는지
3. **init-context.json 참조** — 스크립트가 `init-context.json`을 참조하면 해당 값이 최신인지

---

### 2-6. `settings.json` — 설정 일관성 검증

1. **hooks 설정** — 등록된 훅의 command 경로가 실제 파일과 매칭되는지
2. **formatCommand 정합성** — PostToolUse 훅의 포맷 명령이 `init-context.json`의 `formatCommand`와 일치하는지
3. **플러그인 적합성** — 등록된 플러그인이 현재 프로젝트에 적합한지 확인한다. 부적합한 플러그인이 발견되면 사용자에게 비활성화 여부를 확인하고, 승인 시 `settings.json`에서 해당 플러그인을 `false`로 변경한다

---

### 2-7. `init-context.json` — 메타데이터 갱신

CLAUDE.md에서 파악한 정보로 `init-context.json`을 **최신 상태로 갱신**한다:

- `language`, `framework`, `packageManager` — CLAUDE.md 기술 스택에서 추출
- `formatter`, `formatCommand` — CLAUDE.md 또는 빌드 파일에서 추출
- `rulesExists` — `rules/` 디렉토리 존재 여부
- `prdExists` — `docs/PRD.md` 존재 여부
- `roadmapExists` — `docs/roadmaps/` 존재 여부
- `claudeMdExists` — `CLAUDE.md` 존재 여부
- `settingsExists` — `settings.json` 존재 여부

---

## STEP 3 — 교차 일관성 검증

모든 파일 동기화 후, 전체를 아우르는 일관성을 검증한다:

| 검증 항목 | 대조 대상 |
|---|---|
| `rules/coding-conventions.md`의 포맷터 언급 | `settings.json`의 PostToolUse 훅 |
| `rules/constraints.md`의 금지 항목 | `rules/coding-conventions.md`의 컨벤션 |
| `commands/`의 상호 참조 | 실제 커맨드 파일 존재 여부 |
| `agents/`의 트리거 조건 | `rules/document-sync.md`의 위임 규칙 |
| `agents/` 본문의 기술 스택 참조 (템플릿, 가이드라인 포함) | CLAUDE.md 기술 스택 |
| `settings.json`의 hooks.command 경로 | `hooks/` 실제 파일 |
| `init-context.json` 값 | CLAUDE.md 기술 스택 |
| `scripts/`의 참조 값 | `init-context.json` |

불일치 발견 시 자동 수정하되, **리포트에 변경 내역을 명시**한다.

---

## STEP 4 — CLAUDE.md 초기화 마커 제거

`CLAUDE.md`에 아래 블록이 존재하면 **제거**한다:

```markdown
> **⚠️ 초기화 미완료 — `/sync-claude`를 실행하세요.**
> 현재 .claude/ 설정이 이 프로젝트에 맞지 않습니다. 작업 전에 반드시 `/sync-claude`를 실행하세요.
> 이 블록은 `/sync-claude` 완료 시 자동으로 제거됩니다.
```

하위 호환을 위해 기존 `/init-rules` 마커도 감지하여 제거한다:

```markdown
> **⚠️ 초기화 미완료 — `/init-rules`를 실행하세요.**
> 현재 rules 파일이 이 프로젝트에 맞지 않습니다. 작업 전에 반드시 `/init-rules`를 실행하세요.
> 이 블록은 `/init-rules` 완료 시 자동으로 제거됩니다.
```

제거 후 빈 줄이 연속되면 하나로 정리한다.

---

## STEP 5 — 완료 리포트 출력

모든 동기화 완료 후 아래 형식으로 출력한다.

```
---

## 프로젝트 초기화 [████░░░░] 2/3 완료

> **세팅 → 동기화 → 계획**

### 동기화 결과

| 대상 | 판정 | 처리 |
|---|---|---|
| `rules/` | {ok/mismatch/missing} | {보완 N개 / 재작성 N개 / 신규 생성 N개} |
| `commands/` | {ok/mismatch} | {수정 N개 / 이상 없음} |
| `agents/` | {ok/mismatch} | {수정 N개 / 이상 없음} |
| `hooks/` | {ok/issue} | {수정 N개 / 이상 없음} |
| `scripts/` | {ok/issue} | {수정 N개 / 이상 없음} |
| `settings.json` | {ok/mismatch} | {수정 / 이상 없음} |
| `init-context.json` | {ok/outdated} | {갱신 / 이상 없음} |

### 교차 검증 결과
- ✅ 전체 일관성 확인 완료
{또는}
- ⚠️ {불일치 항목}: {수정 내용}

### 변경된 파일
- ✅ {파일 경로} — {변경 요약}
(변경 없으면 이 섹션 생략)

### TODO (사용자 확인 필요)
- ⚠️ {확인 필요한 항목}
(없으면 이 섹션 생략)

---

### 초기화 진행 상황

| 단계 | 커맨드 | 상태 |
|---|---|---|
| 1 | `/init` | ✅ 완료 |
| 2 | `/sync-claude` | ✅ 완료 |
| 3 | `/plan-feature` | {prd/roadmap 기반 상태 메시지} |

---

> 다음: `/plan-feature`

### 수정 요청
특정 파일을 수정하려면:
> "coding-conventions.md에서 네이밍 규칙을 snake_case로 변경해줘"
```

### 상태 메시지 결정 규칙

**3단계 `/plan-feature` 상태:**

- `docs/PRD.md` 없음 → `⚠️ PRD 없음 — 반드시 실행`
- `docs/PRD.md` 있고 `docs/roadmaps/` 없음 → `⚠️ ROADMAP 없음`
- 둘 다 존재 → `✅ PRD+ROADMAP 존재 — 건너뛰기 가능`

### 출력 규칙

- **프로그레스 바**는 리포트 최상단에 배치한다
- **"세팅 → 동기화 → 계획"** 니모닉은 프로그레스 바 바로 아래에 배치한다
- **리포트의 마지막 커맨드 안내는 반드시 `> 다음: /plan-feature`**로 출력한다 (PRD+ROADMAP 모두 있으면
  `> 초기화 완료! 이제 개발을 시작하세요.`)
