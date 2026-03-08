# 제약 사항

## 절대 금지

- **CLAUDE.md 수정 없이 기술 스택 변경** 금지
- **`api` 모듈에 구현체 배치** 금지 — 인터페이스/DTO/예외만 포함
- **필드 주입 (`@Autowired`)** 금지 — 생성자 주입만 사용
- **환경 변수를 코드에 하드코딩** 금지 — `application*.yml` + `@Value`/`@ConfigurationProperties`로 관리
- **로드맵 순서 무시** 금지 — Phase/Task 순서 엄수
- **`impl` → 다른 `impl` 직접 의존** 금지 — 반드시 `api` 모듈을 통해 의존

## 주의 사항

- `.env` 파일을 Git에 커밋하지 않는다
- `application-local.yml`, `application-prod.yml`은 `.gitignore`에 포함되어 있으므로 Git에 커밋되지 않음을 인지한다
- 외부 라이브러리 추가 시 CLAUDE.md 기술 스택 섹션 갱신
- `./gradlew build`가 항상 성공하는 상태를 유지한다
- 기존 API 인터페이스 변경 시 반드시 사전 공유

## 파일 수정 범위

- 요청 범위를 벗어난 파일은 수정하지 않는다
- 리팩토링이 필요하면 별도 제안으로 분리한다
