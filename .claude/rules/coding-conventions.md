# 코딩 컨벤션

## 네이밍

- 클래스명: PascalCase (`QuizService`, `QuizResponseDto`)
- 메서드/변수: camelCase (`findById`, `quizTitle`)
- 상수: SCREAMING_SNAKE_CASE (`MAX_RETRY_COUNT`)
- 패키지: 소문자 (`com.icc.qasker.quiz`)
- 파일명: 클래스명과 동일 (Java 필수 규칙)
- 코드 주석: 한국어

## 클래스 구조

- 하나의 파일에 하나의 public 클래스
- 필드 → 생성자 → public 메서드 → private 메서드 순서
- Lombok `@RequiredArgsConstructor`로 생성자 주입 (필드 주입 `@Autowired` 금지)
- DTO는 `record` 또는 Lombok `@Getter` + `@Builder` 사용

## 모듈 구조 규칙

- `api` 모듈: 인터페이스, DTO, 예외 정의만 포함 (구현체 금지)
- `impl` 모듈: 서비스 구현체, 리포지토리, 설정 클래스
- `global` 모듈: 전역 예외 처리, 공통 응답 DTO, BaseEntity
- 모듈 간 의존 방향: `impl` → `api` (역방향 금지)

## Import 순서

1. `java.*` / `javax.*` (표준 라이브러리)
2. `org.*` / `com.*` (서드파티)
3. `com.icc.qasker.*` (프로젝트 내부)
4. `static` import

## API 컨벤션

- REST API 경로: kebab-case (`/api/v1/quiz-sets`)
- 응답은 공통 응답 DTO로 래핑
- 예외는 `global` 모듈의 `GlobalExceptionHandler`에서 처리

## 기타

- 비동기: async/await 패턴 불가 → `@Async` + `CompletableFuture` 사용
- 환경 변수: `application*.yml`에서 `@Value` 또는 `@ConfigurationProperties`로 주입
- 로깅: `@Slf4j` (Lombok) 사용
