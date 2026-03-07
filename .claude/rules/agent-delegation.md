# 에이전트 위임 규칙

작업 유형에 따라 반드시 해당 전용 에이전트(Task tool의 subagent_type)를 사용해야 합니다.

| 작업 유형                      | 전용 에이전트          | 비고                               |
| ------------------------------ | ---------------------- | ---------------------------------- |
| UI 컴포넌트 생성/수정 (마크업) | `ui-markup-specialist` | Tailwind + shadcn/ui 스타일링 전담 |
| 코드 구현 후 리뷰              | `code-reviewer`        | 구현 완료 후 자동 실행 권장        |
| PRD 작성                       | `prd-generator`        | 새 기능 요구사항 정리 시           |
| ROADMAP.md 갱신                | `development-planner`  | Task 완료/추가 시                  |
| 코드베이스 탐색                | `Explore`              | 3회 이상 검색이 필요한 탐색 작업   |
| 구현 계획 수립                 | `Plan`                 | 아키텍처 결정이 필요한 작업        |
| Git 커밋                       | `/commit` 스킬 사용    | Skill tool로 호출                  |
| 프롬프트 검증                  | `/validate` 스킬 사용  | Skill tool로 호출                  |

## 사용 가능한 스킬

| 스킬              | 설명                                        |
| ----------------- | ------------------------------------------- |
| `/commit`         | 이모지 + 컨벤셔널 커밋 메시지 생성          |
| `/validate`       | 퀴즈 메타프롬프트 검증 (듀얼 에이전트 토론) |
| `/plan-feature`   | 새 기능/대규모 작업 계획 수립               |
| `/init`           | 프로젝트 초기화 (원스톱)                    |
| `/init-rules`     | Rules 파일 초기화                           |
| `history-logger`  | Notion CLAUDE_LOGS에 히스토리 기록          |
| `cs-logger`       | Notion에 CS 개념 기록                       |
| `simplify`        | 변경 코드 리뷰 및 간소화                    |
| `frontend-design` | 프론트엔드 UI 생성 (높은 디자인 품질)       |

## 에이전트 사용 원칙

- 독립적인 작업은 **병렬로** 여러 에이전트를 동시 실행한다
- UI 마크업 변경은 직접 수행하지 않고 반드시 `ui-markup-specialist`에 위임한다
- 코드 구현이 완료되면 `code-reviewer`를 실행하여 품질을 검증한다
- 단순 파일 읽기/검색은 에이전트 없이 Glob, Grep, Read 도구를 직접 사용한다
