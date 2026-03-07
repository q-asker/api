# /init — 프로젝트 초기화 (원스톱)

## 역할

새 프로젝트에 Claude Code 환경을 한 번에 세팅한다.
`.claude/`를 다른 프로젝트에서 통째로 복사한 상황에서도 정상 작동한다.

**이 커맨드가 처리하는 것:**

- CLAUDE.md 생성/보완
- .claude/settings.json 확인/생성

**이 커맨드가 처리하지 않는 것 (후속 커맨드에 위임):**

- rules 파일 생성/수정 → `/init-rules`
- PRD/ROADMAP 생성 → `/plan-feature`

---

## STEP 1 — 컨텍스트 수집

아래 7가지를 순서대로 스캔한다. 각 항목의 결과를 내부 변수로 보관한다.

### 1-1. 빌드/패키지 설정

다음 파일을 탐색하여 언어, 프레임워크, 패키지 매니저를 추론한다:

| 파일                                                               | 추론 대상                             |
| ------------------------------------------------------------------ | ------------------------------------- |
| `package.json`                                                     | Node.js 생태계, scripts, dependencies |
| `package-lock.json` / `yarn.lock` / `pnpm-lock.yaml` / `bun.lockb` | 패키지 매니저                         |
| `tsconfig.json`                                                    | TypeScript 버전, 경로 alias           |
| `next.config.*` / `vite.config.*` / `nuxt.config.*`                | 프레임워크                            |
| `pyproject.toml` / `requirements.txt` / `Pipfile`                  | Python 생태계                         |
| `go.mod` / `Cargo.toml` / `*.sln` / `*.csproj`                     | 기타 언어                             |
| `Makefile` / `Dockerfile` / `docker-compose.*`                     | 빌드/배포 도구                        |

파악 불가능한 항목은 `TODO` placeholder로 표기한다.

→ 결과: `{language}`, `{framework}`, `{packageManager}`, `{mainDependencies}`

### 1-2. 디렉토리 구조 탐색

프로젝트 규모에 맞게 depth를 조절하여 디렉토리 트리를 파악한다.
`node_modules/`, `.git/`, `dist/`, `build/`, `.next/` 등은 제외한다.

→ 결과: `{directoryTree}`

### 1-3. 기존 CLAUDE.md 확인

- 존재하면 → 내용을 읽어 누락 섹션 파악. 기존 내용은 보존하고 누락 섹션만 보완한다.
- 없으면 → 전체 신규 생성

→ 결과: `{claudeMdExists}` (true/false)

### 1-4. .claude/rules/ 일치성 검증

- 디렉토리 없거나 비어있으면 → `{rulesStatus}` = **missing**
- 파일이 있으면 → rules 내의 기술 스택 키워드(프레임워크명, 라이브러리명)를 1-1에서 추론한 기술 스택과 대조
  - 현재 프로젝트에 없는 기술 스택이 rules에 언급되어 있으면 → `{rulesStatus}` = **mismatch**
  - 일치하면 → `{rulesStatus}` = **ok**

### 1-5. docs/ 존재 여부 확인

- `docs/PRD.md` 존재 여부 → `{prdExists}`
- `docs/roadmaps/ROADMAP_v*.md` 존재 여부 → `{roadmapExists}`

### 1-6. .claude/settings.json 확인

- 존재하면 → 내용 읽기, `plansDirectory` 필드 존재 여부 확인
- 없으면 → STEP 3에서 생성

→ 결과: `{settingsExists}` (true/false)

### 1-7. Shrimp Task Manager 연동 확인

다음 중 하나라도 존재하면 `{shrimpConnected}` = true:

- `shrimp-rules.md` 파일
- `shrimp_data/` 디렉토리

→ 결과: `{shrimpConnected}` (true/false)

### 1-8. JIRA 브랜치 감지

현재 Git 브랜치 이름에서 JIRA 프로젝트 키 패턴을 추출한다.

- `git branch --show-current`로 현재 브랜치명을 가져온다
- 정규식 `^([A-Z]+-\d+)` 또는 `([A-Z]+-\d+)` 패턴으로 JIRA 티켓 번호를 추출한다
  - 예: `ICC-266-claude-code` → `ICC-266`
  - 예: `feature/ICC-123-login` → `ICC-123`
- 추출 성공 시 → `{jiraTicket}` = 추출된 티켓 번호 (예: `ICC-266`)
- 추출 실패 시 → `{jiraTicket}` = null

→ 결과: `{jiraTicket}` (string | null)

---

## STEP 2 — CLAUDE.md 작성

`{claudeMdExists}` = true이면 누락 섹션만 보완 (기존 내용 보존).
`{claudeMdExists}` = false이면 전체 신규 생성.

아래 섹션을 포함하여 작성한다. 프로젝트에 해당하지 않는 선택 섹션은 생략한다.
형식(테이블, 리스트 등)은 내용에 맞게 자유롭게 선택한다.

### 초기화 미완료 마커 삽입

`{rulesStatus}`가 **missing** 또는 **mismatch**이면, CLAUDE.md 최상단(`# CLAUDE.md` 바로 아래)에 다음 블록을 삽입한다:

```markdown
> **⚠️ 초기화 미완료 — `/init-rules`를 실행하세요.**
> 현재 rules 파일이 이 프로젝트에 맞지 않습니다. 작업 전에 반드시 `/init-rules`를 실행하세요.
> 이 블록은 `/init-rules` 완료 시 자동으로 제거됩니다.
```

이 마커는 CLAUDE.md가 매 세션 자동 로드되므로, 개발자가 다른 작업을 요청해도 Claude Code가 이를 읽고 먼저 `/init-rules` 실행을 안내하게 된다.

`{rulesStatus}` = **ok**이면 마커를 삽입하지 않는다. 기존 CLAUDE.md에 마커가 있으면 제거한다.

### 필수 섹션

1. **문서 참조 블록** (초기화 마커 아래)
   - `{prdExists}` = true → `> 제품 요구사항은 docs/PRD.md 참조.` 활성화
   - `{prdExists}` = false → `<!-- TODO: docs/PRD.md 생성 후 활성화 -->` 주석
   - `{roadmapExists}` = true → `> 로드맵은 docs/roadmaps/ROADMAP_v*.md 참조.` 활성화
   - `{roadmapExists}` = false → `<!-- TODO: docs/roadmaps/ 생성 후 활성화 -->` 주석

2. **프로젝트 개요** — 목적과 핵심 기능 1~2줄

3. **기술 스택** — 언어, 프레임워크, 주요 라이브러리

4. **명령어 (Scripts)** — package.json scripts 또는 Makefile 타겟 기반

5. **아키텍처** — 디렉토리 구조 필수, 하위 항목은 프로젝트에 맞게 구성

6. **개발 도구 및 설정** — 패키지 매니저, 런타임 버전, 포맷터/린터

7. **CLAUDE.md 유지 규칙** — 아래 내용을 그대로 포함:

```markdown
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
```

### 선택 섹션 (해당 시에만)

- **환경 변수** — .env 파일이 있으면 키 목록 (값은 제외). 없으면 생략
- **라우팅 구조** — 웹 앱이면 라우트 테이블. 아니면 생략
- **데이터 플로우** — 복잡한 데이터 흐름이 있을 때만

---

## STEP 2.5 — JIRA 커밋 접두사 Git Hook 설정

`{jiraTicket}`이 null이 아닌 경우에만 이 단계를 실행한다.

`.claude/scripts/setup-git-hooks.sh`를 실행한다. 스크립트가 훅 생성, 사용자 확인, hooksPath 설정을 모두 처리한다.

설치 완료 후, 팀원이 clone 시 자동 적용되도록 프로젝트의 빌드 도구에 `.claude/scripts/setup-git-hooks.sh --auto` 실행을 통합한다.

→ 결과: `{jiraHookInstalled}` (true/false/skipped)

---

## STEP 3 — 완료 리포트 출력

아래 형식을 **반드시 그대로** 출력한다. 프로그레스 바와 다음 커맨드 안내가 핵심이다.

```
---

## 프로젝트 초기화 [██░░░░░░] 1/3 완료

> **세팅 → 규칙 → 계획**

### 작업 내역
- [{생성 | 보완}] CLAUDE.md
- [{생성 | 확인 | 보완}] .claude/settings.json
- [{설치 | 거부 | 미감지}] JIRA 커밋 접두사 Git Hook

### 채워진 섹션
- ✅ 프로젝트 개요
- ✅ 기술 스택: {파악된 스택 요약}
- ✅ 명령어
- ✅ 아키텍처
- ✅ 개발 도구 및 설정
- ...

### TODO (사용자 확인 필요)
- ⚠️ {항목}: {파악 불가 이유}
(없으면 이 섹션 생략)

---

### 초기화 진행 상황

| 단계 | 커맨드           | 상태                          |
| ---- | ---------------- | ----------------------------- |
| 1    | `/init`          | ✅ 완료                       |
| 2    | `/init-rules`    | {rulesStatus 기반 상태 메시지} |
| 3    | `/plan-feature`  | {prd/roadmap 기반 상태 메시지} |

{shrimpConnected = false인 경우에만}
💡 **Shrimp Task Manager**를 설치하면 체계적 작업 관리가 가능합니다. (선택)

---

> 다음: `/init-rules`
```

### 상태 메시지 결정 규칙

**2단계 `/init-rules` 상태:**

- `{rulesStatus}` = missing → `⚠️ rules 없음 — 반드시 실행`
- `{rulesStatus}` = mismatch → `⚠️ rules 불일치 — 반드시 실행`
- `{rulesStatus}` = ok → `✅ 일치 확인됨 — 건너뛰기 가능`

**3단계 `/plan-feature` 상태:**

- `{prdExists}` = false → `⚠️ PRD 없음 — 반드시 실행`
- `{prdExists}` = true, `{roadmapExists}` = false → `⚠️ ROADMAP 없음`
- 둘 다 존재 → `✅ PRD+ROADMAP 존재 — 건너뛰기 가능`

### 출력 규칙

- **프로그레스 바**는 반드시 리포트 최상단에 배치한다
- **"세팅 → 규칙 → 계획"** 니모닉은 프로그레스 바 바로 아래에 배치한다
- **초기화 진행 상황 테이블**은 반드시 포함한다
- **리포트의 마지막 줄은 반드시 `> 다음: /init-rules`**로 끝낸다 (rulesStatus=ok이고 PRD도 있으면 `> 초기화 완료! 이제 개발을 시작하세요.`)
