---
description: "이모지와 컨벤셔널 커밋 메시지로 잘 포맷된 커밋을 생성합니다"
allowed-tools:
  [
    "Bash(git add:*)",
    "Bash(git status:*)",
    "Bash(git commit:*)",
    "Bash(git diff:*)",
    "Bash(git log:*)",
    "Read",
    "Edit",
  ]
---

# Claude 명령어: Commit

이모지와 컨벤셔널 커밋 메시지로 잘 포맷된 커밋을 생성합니다.

## 사용법

```
/commit
```

## 프로세스

1. untracked 상태인 파일들을 스테이지에 포함시키고 스테이지된 파일 확인
2. 여러 논리적 변경사항에 대한 diff 분석
3. 필요시 분할 제안
4. **문서 동기화 체크** (아래 규칙 참조)
5. 이모지 컨벤셔널 포맷으로 커밋 생성

## 문서 동기화 체크 (Step 4)

커밋 직전, diff에 포함된 파일을 아래 패턴과 대조한다.
하나라도 매칭되면 CLAUDE.md의 해당 섹션이 최신 상태인지 확인하고, 갱신이 필요하면 **커밋 전에 사용자에게 알린다.**

### 감지 패턴

| 패턴                  | 감지 조건                                             | 확인할 CLAUDE.md 섹션    |
| --------------------- | ----------------------------------------------------- | ------------------------ |
| `**/build.gradle`     | `implementation`, `api`, `compileOnly` 라인 추가/삭제 | 기술 스택                |
| `settings.gradle`     | `include` 라인 변경                                   | 아키텍처 (모듈 목록)     |
| `modules/*/`          | 새 디렉토리 생성 또는 삭제                            | 아키텍처 (디렉토리 트리) |
| `**/application*.yml` | 새 속성 키 추가                                       | 환경 변수 또는 개발 도구 |
| `.env*`               | 변수 추가/삭제                                        | 환경 변수                |
| `docker-compose*.yml` | `services:` 하위 서비스 추가/삭제                     | 개발 도구                |
| `.github/workflows/*` | 파일 추가/변경                                        | 개발 도구                |
| `build.gradle` (루트) | `plugins`, `version` 변경                             | 기술 스택, 개발 도구     |

### 동작 규칙

- 매칭되는 패턴이 **없으면** → 바로 커밋 진행
- 매칭되는 패턴이 **있으면** → 해당 섹션을 읽고 실제 변경 내용과 비교
  - 이미 최신이면 → 바로 커밋 진행
  - 갱신 필요하면 → 사용자에게 알림:
    ```
    ⚠️ 문서 동기화 필요
    - CLAUDE.md [기술 스택]: build.gradle에 새 의존성 추가 감지
    갱신 후 커밋할까요, 아니면 그대로 커밋할까요?
    ```
  - 사용자가 "갱신" 선택 → CLAUDE.md 수정 후 함께 커밋
  - 사용자가 "그대로" 선택 → 그대로 커밋 진행

## 커밋 포맷

`<이모지> <타입>: <설명>`

**타입:**

- `feat`: 새로운 기능
- `fix`: 버그 수정
- `docs`: 문서화
- `style`: 포맷팅
- `refactor`: 코드 리팩토링
- `perf`: 성능 개선
- `test`: 테스트
- `chore`: 빌드/도구

**규칙:**

- 명령형 어조 ("추가" not "추가됨")
- 첫 줄 72자 미만
- 원자적 커밋 (단일 목적)
- 관련 없는 변경사항 분할

## 이모지 맵

✨ feat | 🐛 fix | 📝 docs | 💄 style | ♻️ refactor | ⚡ perf | ✅ test | 🔧 chore | 🚀 ci | 🚨 warnings | 🔒️ security | 🚚 move | 🏗️ architecture | ➕ add-dep | ➖ remove-dep | 🌱 seed | 🧑‍💻 dx | 🏷️ types | 👔 business | 🚸 ux | 🩹 minor-fix | 🥅 errors | 🔥 remove | 🎨 structure | 🚑️ hotfix | 🎉 init | 🔖 release | 🚧 wip | 💚 ci-fix | 📌 pin-deps | 👷 ci-build | 📈 analytics | ✏️ typos | ⏪️ revert | 📄 license | 💥 breaking | 🍱 assets | ♿️ accessibility | 💡 comments | 🗃️ db | 🔊 logs | 🔇 remove-logs | 🙈 gitignore | 📸 snapshots | ⚗️ experiment | 🚩 flags | 💫 animations | ⚰️ dead-code | 🦺 validation | ✈️ offline

## 분할 기준

다른 관심사 | 혼합된 타입 | 파일 패턴 | 큰 변경사항

## 참고사항

- 스테이지된 파일이 있으면 해당 파일만 커밋
- 분할 제안을 위한 diff 분석
- **커밋에 Claude 서명 절대 추가하지 않음**
