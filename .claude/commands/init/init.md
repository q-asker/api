# /init — 프로젝트 초기화 (원스톱)

## 역할

새 프로젝트에 Claude Code 환경을 한 번에 세팅한다.
`.claude/`를 다른 프로젝트에서 통째로 복사한 상황에서도 정상 작동한다.

**처리 범위:** CLAUDE.md 생성/보완, .claude/settings.json 확인/생성
**후속 위임:** .claude/ 동기화 → `/sync-claude`, PRD/ROADMAP → `/plan-feature`

---

## STEP 1 — 컨텍스트 수집

프로젝트의 빌드 파일, 설정 파일, 디렉토리 구조를 직접 읽고 분석한다.
분석 결과를 `.claude/init-context.json`의 스키마에 맞춰 Write한다.

### 1-1. rules 일치성 검증

`rulesExists`가 true이면 rules 파일들의 내용을 읽고, 기술 스택 키워드를 `language`, `framework`와 대조하여 **ok** / \*
\*mismatch**를 판정한다. false이면 **missing\*\*으로 판정한다.

### 1-2. 디렉토리 구조 탐색

프로젝트 규모에 맞게 depth를 조절하여 디렉토리 트리를 파악한다.

---

## STEP 2 — CLAUDE.md 작성

`init-context.json`의 `claudeMdExists`가 true이면 누락 섹션만 보완(기존 내용 보존), false이면 전체 신규 생성한다.

### 초기화 미완료 마커

rules 상태가 **missing** 또는 **mismatch**이면 `# CLAUDE.md` 바로 아래에 삽입한다. **ok**이면 삽입하지 않고, 기존 마커가 있으면
제거한다. 다른 스킬이 이 마커를 감지하므로 문구를 정확히 유지한다:

```markdown
> **⚠️ 초기화 미완료 — `/sync-claude`를 실행하세요.**
> 현재 .claude/ 설정이 이 프로젝트에 맞지 않습니다. 작업 전에 반드시 `/sync-claude`를 실행하세요.
> 이 블록은 `/sync-claude` 완료 시 자동으로 제거됩니다.
```

### 문서 참조 블록

초기화 마커 아래에 배치. `init-context.json`의 `prdExists`, `roadmapExists`를 참조하여 존재하면 활성화, 없으면 주석 처리:

- 존재 → `> 제품 요구사항은 docs/PRD.md 참조.` / `> 로드맵은 docs/roadmaps/ROADMAP_v*.md 참조.`
- 미존재 → `<!-- TODO: docs/PRD.md 생성 후 활성화 -->` / `<!-- TODO: docs/roadmaps/ 생성 후 활성화 -->`

### 필수 섹션

1. **프로젝트 개요** — 목적과 핵심 기능 1~2줄
2. **기술 스택** — 언어, 프레임워크, 주요 라이브러리
3. **명령어 (Scripts)** — 빌드 도구의 태스크/스크립트 기반
4. **아키텍처** — 디렉토리 구조 필수
5. **개발 도구 및 설정** — 패키지 매니저, 런타임 버전, 포맷터/린터

---

## STEP 2.5 — 포맷팅 설정

### formatter가 감지된 경우

`.claude/settings.json`의 `hooks.PostToolUse`에 포맷팅 훅을 추가한다.
`init-context.json`의 `formatCommand` 값을 command로 사용한다. 기존 hooks가 있으면 병합한다.

### formatter가 null인 경우

사용자에게 포맷터 도입을 제안한다. 사용자가 선택하면 해당 포맷터를 프로젝트에 설치하고(빌드 파일 수정), 훅을 설정한다. 거부하면 건너뛴다.

- `.claude/settings.json`의 `hooks.PostToolUse`에 `Edit|Write` 매처로 `formatCommand`를 등록한다
- Checkstyle처럼 검증만 하는 도구는 훅 대신 CLAUDE.md 명령어 섹션에 기록한다
- 훅 추가 전 사용자에게 확인을 받는다

---

## STEP 2.7 — Git Hook 설정

`.claude/scripts/setup-git-hooks.sh`를 실행한다. 이 스크립트는 `init-context.json`의 `formatter`와
`formatCommand`를 참조하여 **프로젝트에 맞는 훅을 동적으로 생성**한다:

1. **`prepare-commit-msg`** — 브랜치에서 JIRA 티켓을 감지하여 커밋 메시지에 접두사 자동 추가
2. **`pre-commit`** — `init-context.json`의 `formatCommand`에서 파생한 check 명령으로 포맷 위반 시 커밋 차단

### 동작 원칙

- 기존 훅 파일이 있어도 **사양이 다르면 덮어쓴다** (건너뛰지 않음)
- `formatter`가 null이면 pre-commit 훅을 설치하지 않는다
- STEP 2.5에서 `init-context.json`의 `formatCommand`가 확정된 후 실행해야 한다

---

## STEP 3 — 완료 리포트 출력

`init-context.json`의 값을 참조하여 아래 형식을 **그대로** 출력한다.

```
---

## 프로젝트 초기화 [██░░░░░░] 1/3 완료

> **세팅 → 규칙 → 계획**

### 작업 내역
- [{생성 | 보완}] CLAUDE.md
- [{생성 | 확인 | 보완}] .claude/settings.json
- [{설치 | 거부 | 미감지}] JIRA 커밋 접두사 Git Hook
- [{설치 | 거부 | 미감지}] 포맷팅 훅 ({formatter} — {formatCommand})

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

| 단계 | 커맨드          | 상태                                                          |
| ---- | --------------- | ------------------------------------------------------------- |
| 1    | `/init`         | ✅ 완료                                                       |
| 2    | `/sync-claude`  | .claude/ 상태에 따라: ⚠️ 불일치 → 반드시 실행, ✅ 일치 → 건너뛰기 가능 |
| 3    | `/plan-feature` | `prdExists`/`roadmapExists`에 따라 적절히 표시                  |

{`init-context.json`의 `shrimpConnected`가 false인 경우에만}
💡 **Shrimp Task Manager**를 설치하면 체계적 작업 관리가 가능합니다. (선택)

---

> 다음: `/sync-claude`
(모든 단계가 이미 완료 상태면 `> 초기화 완료! 이제 개발을 시작하세요.`)
```
