# CLAUDE.md

> **⚠️ 초기화 미완료 — `/init-rules`를 실행하세요.**
> 현재 rules 파일이 이 프로젝트에 맞지 않습니다. 작업 전에 반드시 `/init-rules`를 실행하세요.
> 이 블록은 `/init-rules` 완료 시 자동으로 제거됩니다.

<!-- TODO: docs/PRD.md 생성 후 활성화 -->
<!-- > 제품 요구사항은 docs/PRD.md 참조. -->
<!-- TODO: docs/roadmaps/ 생성 후 활성화 -->
<!-- > 로드맵은 docs/roadmaps/ROADMAP_v*.md 참조. -->

## 프로젝트 개요

Q-Asker API — 퀴즈 출제·채점 서비스의 백엔드 API 서버. Spring Boot 기반 멀티모듈 구조로 인증, 퀴즈, AI(Gemini), AWS(S3) 기능을 제공한다.

## 기술 스택

| 분류       | 기술                        | 버전            |
| ---------- | --------------------------- | --------------- |
| 언어       | Java                        | 21              |
| 프레임워크 | Spring Boot                 | 3.5.8           |
| ORM        | Spring Data JPA (Hibernate) | —               |
| AI         | Spring AI (Google Gemini)   | 1.1.2           |
| 클라우드   | AWS SDK (S3 등)             | 2.27.24         |
| DB         | MySQL                       | latest (Docker) |
| 빌드       | Gradle                      | 8.14.3          |
| 컨테이너   | Jib (Docker 이미지 빌드)    | 3.4.0           |
| APM        | Scouter                     | 2.17.1          |
| 유틸리티   | Lombok                      | —               |

## 명령어 (Scripts)

| 명령어                     | 설명                       |
| -------------------------- | -------------------------- |
| `./gradlew :app:bootRun`   | 로컬 애플리케이션 실행     |
| `./gradlew :app:bootJar`   | 실행 가능 JAR 빌드         |
| `./gradlew build`          | 전체 빌드                  |
| `./gradlew test`           | 전체 테스트 실행           |
| `./gradlew jib`            | Docker 이미지 빌드 및 푸시 |
| `./gradlew jibDockerBuild` | 로컬 Docker 이미지 빌드    |
| `docker compose up -d`     | 로컬 MySQL + Scouter 실행  |

## 아키텍처

### 멀티모듈 구조 (api/impl 패턴)

각 도메인은 `api` (인터페이스/DTO)와 `impl` (구현체) 모듈로 분리된다. `app` 모듈이 모든 `impl`을 조립하여 실행 가능한 애플리케이션을 구성한다.

```
q-asker/api/
├── app/                        # Spring Boot 메인 애플리케이션 (조립 모듈)
│   ├── src/main/java/com/icc/qasker/
│   │   └── QAskerApplication.java
│   └── src/main/resources/
│       ├── application.yml
│       ├── application-local.yml
│       └── application-prod.yml
├── modules/
│   ├── ai/                     # AI 모듈 (Spring AI + Gemini)
│   │   ├── api/                #   인터페이스, DTO
│   │   └── impl/               #   구현체
│   ├── auth/                   # 인증 모듈 (JWT, OAuth2)
│   │   ├── api/
│   │   └── impl/
│   ├── aws/                    # AWS 모듈 (S3)
│   │   ├── api/
│   │   └── impl/
│   ├── global/                 # 공통 모듈 (예외 처리, 공통 DTO, BaseEntity)
│   ├── quiz/                   # 퀴즈 모듈 (CRUD, 출제, 채점)
│   │   ├── api/
│   │   └── impl/
│   └── util/                   # 유틸리티 모듈
│       ├── api/
│       └── impl/
├── build.gradle                # 루트 빌드 스크립트 (공통 의존성, BOM)
├── settings.gradle             # 모듈 구성
├── docker-compose.yml          # 로컬 개발 환경 (MySQL, Scouter)
└── .env                        # 환경 변수 (Git 미추적)
```

### 패키지 구조

- 루트 패키지: `com.icc.qasker`
- 모듈별 하위 패키지로 도메인 분리

## 환경 변수

### .env (Docker Compose용)

| 키                    | 설명                |
| --------------------- | ------------------- |
| `MYSQL_ROOT_PASSWORD` | MySQL root 비밀번호 |
| `MYSQL_DATABASE`      | 데이터베이스 이름   |
| `MYSQL_USER`          | MySQL 사용자        |
| `MYSQL_PASSWORD`      | MySQL 비밀번호      |

### Jib 빌드 시 필요 (-P 옵션)

| 키                  | 설명                      |
| ------------------- | ------------------------- |
| `DOCKER_ID`         | Docker Hub 사용자명       |
| `DOCKER_IMAGE_NAME` | Docker 이미지 이름        |
| `DOCKER_PASSWORD`   | Docker Hub 비밀번호       |
| `JVM_HEAP_SIZE`     | JVM 힙 메모리 (예: 512m)  |
| `SCOUTER_IP`        | Scouter Collector 서버 IP |
| `SCOUTER_PORT`      | Scouter Collector 포트    |
| `SCOUTER_OBJ_NAME`  | Scouter 오브젝트 이름     |

## 개발 도구 및 설정

| 항목          | 값                                   |
| ------------- | ------------------------------------ |
| 빌드 도구     | Gradle 8.14.3 (wrapper)              |
| JDK           | Amazon Corretto 21 (컨테이너 베이스) |
| 컴파일 옵션   | `-parameters` (파라미터 이름 보존)   |
| JAR 중복 정책 | `DuplicatesStrategy.FAIL`            |
| 프로필        | `local`, `prod`                      |

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
