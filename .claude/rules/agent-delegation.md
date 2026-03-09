# 에이전트 위임 규칙

## 사용 가능한 에이전트

| 에이전트 | 위치 | 용도 |
|---|---|---|
| `code-reviewer` | `.claude/agents/dev/` | 코드 구현 완료 후 품질 리뷰 |
| `development-planner` | `.claude/agents/dev/` | ROADMAP 생성/갱신, Task 관리 |
| `prd-generator` | `.claude/agents/docs/` | PRD 생성/갱신 |

## 사용 가능한 스킬

| 스킬 | 트리거 조건 |
|---|---|
| `/commit` | Git 커밋 생성 시 |
| `/pr` | PR 작성 시 |
| `/plan-feature` | 새 기능/대규모 작업 계획 시 |
| `/sync-claude` | .claude/ 디렉토리 동기화 시 |
| `/init` | 프로젝트 초기화 시 |
| `/docs:sync` | 문서 동기화 시 |
| `/history-logger` | 대화 히스토리 Notion 기록 시 |
| `/cs-logger` | CS 개념 Notion 기록 시 |

## 위임 원칙

- 스킬이 존재하는 작업은 반드시 해당 스킬을 사용한다
- 스킬 없이 처리 가능한 작업은 직접 수행한다
- 스킬 실행 실패 시 사용자에게 알리고 대안을 제시한다
