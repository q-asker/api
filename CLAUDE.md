# Q-Asker API

## 프로젝트 개요

퀴즈 출제·풀이 서비스의 백엔드 API. Spring Boot 기반 멀티모듈 구조로, 인증·퀴즈·AI·AWS 연동 등 도메인별 모듈을 분리하여 관리한다.

## 기술 스택

- **언어**: Java 21 (Amazon Corretto)
- **프레임워크**: Spring Boot 3.5.8
- **빌드 도구**: Gradle (멀티모듈)
- **ORM**: Spring Data JPA
- **DB**: MySQL
- **AI**: Spring AI 1.1.2 (Google Gemini)
- **AWS**: AWS SDK 2.27.24 (S3)
- **인증**: 자체 JWT (auth 모듈)
- **코드 생성**: Lombok
- **검증**: Bean Validation
- **모니터링**: Spring Actuator, Scouter APM
- **컨테이너**: Jib (Docker 이미지 빌드)

## 명령어 (Scripts)

| 명령어                     | 설명                      |
| -------------------------- | ------------------------- |
| `./gradlew build`          | 전체 프로젝트 빌드        |
| `./gradlew :app:bootRun`   | 애플리케이션 로컬 실행    |
| `./gradlew :app:bootJar`   | 실행 가능 JAR 생성        |
| `./gradlew spotlessApply`  | 전체 소스 코드 포맷팅     |
| `./gradlew spotlessCheck`  | 포맷 위반 여부 검증       |
| `./gradlew test`           | 전체 테스트 실행          |
| `./gradlew jib`            | Docker 이미지 빌드·푸시   |
| `./gradlew jibDockerBuild` | 로컬 Docker 이미지 빌드   |
| `docker-compose up -d`     | MySQL + Scouter 로컬 실행 |

## 아키텍처

### 멀티모듈 구조 (api/impl 분리)

```
q-asker/api/
├── app/                          # 실행 모듈 (Spring Boot Application)
│   └── src/main/java/com/icc/qasker/
├── modules/
│   ├── global/                   # 전역 예외 처리, 공통 응답 DTO, BaseEntity
│   ├── auth/
│   │   ├── api/                  # 인증 인터페이스 (JWT, OAuth2)
│   │   └── impl/                 # 인증 구현체
│   ├── quiz/
│   │   ├── api/                  # 퀴즈 인터페이스 (CRUD, 출제·채점)
│   │   └── impl/                 # 퀴즈 구현체
│   ├── ai/
│   │   ├── api/                  # AI 인터페이스 (Gemini 연동)
│   │   └── impl/                 # AI 구현체
│   ├── aws/
│   │   ├── api/                  # AWS 인터페이스 (S3)
│   │   └── impl/                 # AWS 구현체
│   └── util/
│       ├── api/                  # 유틸리티 인터페이스
│       └── impl/                 # 유틸리티 구현체 (HealthCheck 등)
├── build.gradle                  # 루트 빌드 설정 (BOM, Spotless, 공통 의존성)
├── settings.gradle               # 모듈 정의
├── docker-compose.yml            # MySQL + Scouter 로컬 환경
├── .githooks/                    # Git 훅 (prepare-commit-msg, pre-commit, pre-push)
└── .github/workflows/            # CI/CD (ci.yml, prod_deploy.yml, auto-version-bump.yml)
```

### 모듈 의존성 방향

`app` → `*-impl` → `*-api` → `global`

- **app**: 모든 impl 모듈을 조립하여 실행 가능한 애플리케이션 구성
- **impl**: 비즈니스 로직 구현, 자신의 api 모듈에만 의존
- **api**: 인터페이스·DTO 정의, global에만 의존
- **global**: 공통 유틸리티, 외부 모듈에 의존하지 않음

## 개발 도구 및 설정

- **패키지 매니저**: Gradle Wrapper
- **JDK**: Java 21 (toolchain으로 자동 다운로드)
- **포맷터**: Spotless (Google Java Format)
  - 적용: `./gradlew spotlessApply`
  - 검증: `./gradlew spotlessCheck`
- **EditorConfig**: 2-space indent (Java), 4-space indent (Gradle)
- **Git Hooks**: `.githooks/` 디렉토리 사용 (`git config core.hooksPath .githooks`)
  - `prepare-commit-msg`: 브랜치에서 JIRA 티켓 감지 → 커밋 접두사 자동 추가
  - `pre-commit`: `spotlessCheck` → 포맷 위반 시 커밋 차단
  - `pre-push`: `spotlessCheck` → 포맷 위반 시 푸시 차단
- **CI/CD**: GitHub Actions (`ci.yml`, `prod_deploy.yml`, `auto-version-bump.yml`)
- **코드 리뷰**: CodeRabbit (`.coderabbit.yaml`)

## 환경 변수

Docker Compose용 (`.env`):

- `MYSQL_ROOT_PASSWORD` — MySQL root 비밀번호
- `MYSQL_DATABASE` — 데이터베이스 이름
- `MYSQL_USER` — MySQL 사용자
- `MYSQL_PASSWORD` — MySQL 비밀번호

Gradle 프로퍼티 (배포 시):

- `DOCKER_ID` — Docker Hub 사용자 ID
- `DOCKER_PASSWORD` — Docker Hub 비밀번호
- `DOCKER_IMAGE_NAME` — Docker 이미지 이름
- `JVM_HEAP_SIZE` — JVM 힙 메모리 크기
- `SCOUTER_IP` / `SCOUTER_PORT` / `SCOUTER_OBJ_NAME` — Scouter APM 설정
