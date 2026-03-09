---
description: "Java/Spring Boot 코딩 컨벤션, 멀티모듈 의존성 규칙, 네이밍, 제약 사항"
globs: "**/*.java"
---

# 코드 규칙

## 네이밍

- 클래스: PascalCase (`QuizService`, `AuthController`)
- 메서드/변수: camelCase (`createQuiz`, `quizTitle`)
- 상수: SCREAMING_SNAKE_CASE (`MAX_RETRY_COUNT`)
- 패키지: 소문자 (`com.icc.qasker.quiz`)
- 코드 주석: 한국어

## 멀티모듈 의존성 규칙

- **의존 방향**: `app` → `*-impl` → `*-api` → `global`
- **역방향 의존 금지**: api 모듈이 impl 모듈을 참조하지 않는다
- **모듈 간 횡단 참조 금지**: `quiz-impl`이 `auth-impl`을 직접 참조하지 않는다 (api를 통해서만)
- **api 모듈**: 인터페이스, DTO, enum만 정의 (구현체 금지)
- **impl 모듈**: 비즈니스 로직 구현, Repository, Entity 정의

## 클래스 구조

- **Controller**: 요청/응답 매핑만 담당, 비즈니스 로직 금지
- **Service**: 비즈니스 로직 집중, 트랜잭션 관리
- **Repository**: Spring Data JPA 인터페이스 사용
- **Entity**: JPA 엔티티, `global` 모듈의 BaseEntity 상속
- **DTO**: record 또는 클래스 + Lombok, Bean Validation 어노테이션 사용

## Lombok 사용 규칙

- `@Getter`, `@Builder`, `@RequiredArgsConstructor` 적극 활용
- `@Setter` 지양 — 불변 객체 우선
- `@Data` 지양 — 필요한 어노테이션만 개별 선언

## 포맷팅

- **Spotless + Google Java Format** 적용
- indent: 2-space (Google Java Format 기본값)
- 파일 끝 개행 필수
- 사용하지 않는 import 금지

## 제약 사항

- **CLAUDE.md 수정 없이 기술 스택 변경** 금지
- **환경 변수를 코드에 하드코딩** 금지 — `application.yml` + `@Value` 또는 `@ConfigurationProperties` 사용
- **비동기**: `@Async` 또는 `CompletableFuture` 사용
- **예외 처리**: `global` 모듈의 GlobalExceptionHandler 활용, 커스텀 예외 정의
- **API 응답**: `global` 모듈의 `ApiResponse<T>` 래퍼 사용
