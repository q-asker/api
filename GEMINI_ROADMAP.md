# Spring AI + Gemini + PDF Context Caching 구현 로드맵

## Context

**Spring AI (`spring-ai-starter-model-google-genai`) + Gemini API**로 퀴즈 생성 파이프라인을 구현한다.
Spring AI의 `ChatModel`, `GoogleGenAiCachedContentService`를 활용하되,
Spring AI가 지원하지 않는 **File Upload API**만 `RestClient`로 직접 구현하는 하이브리드 구조이다.

**기술 결정 — Spring AI 도입 근거**:

- `GoogleGenAiCachedContentService`가 Context Caching CRUD를 내장 지원
- `CachedContentRequest.contents`가 `List<com.google.genai.types.Content>` 타입이므로 **PDF fileData도 캐싱
  가능**
- `ChatModel.call()` + `GoogleGenAiChatOptions`로 generateContent 호출 → DTO 보일러플레이트 제거
- `responseMimeType: application/json` + `BeanOutputConverter`로 Structured Output 처리
- File Upload API만 미지원 → 이 1개 엔드포인트만 RestClient로 직접 구현

**학습 방식**: 각 Step을 순서대로 진행하며, 대화형 AI에게 질문 → 코드를 직접 타이핑하는 방식으로 구현한다. 각 Step은 독립적으로 테스트 가능하다.

---

## Step 1: Spring AI + Gemini "Hello World"

**목표**: Spring AI를 통해 Gemini에 텍스트를 보내고 응답을 받는 최소 동작 확인

**학습 포인트**:

- Spring AI의 `ChatModel` / `ChatClient` 추상화 구조
- `spring-ai-starter-model-google-genai` 자동 설정 방식
- Spring AI BOM (Bill of Materials) 도입과 버전 관리
- `application.yml` 프로퍼티만으로 모델 연결

**수정할 파일**:

- `build.gradle` (루트) — Spring AI BOM 추가
  ```groovy
  dependencyManagement {
      imports {
          mavenBom "org.springframework.ai:spring-ai-bom:1.0.0"
      }
  }
  ```
- `modules/ai/build.gradle` — `spring-ai-starter-model-google-genai` 추가
  ```groovy
  implementation platform("org.springframework.ai:spring-ai-bom:1.0.0")
  implementation "org.springframework.ai:spring-ai-starter-model-google-genai"
  ```
- `app/src/main/resources/application-local.yml` — Gemini 설정 추가
  ```yaml
  spring:
    ai:
      google:
        genai:
          api-key: ${GEMINI_API_KEY}
          chat:
            options:
              model: gemini-2.0-flash
              temperature: 0.7
  ```

**테스트 코드**:

```java

@Autowired
ChatModel chatModel;

ChatResponse response = chatModel.call(
    new Prompt("안녕하세요, 간단한 퀴즈 하나 만들어주세요.")
);
String text = response.getResult().getOutput().getText();
```

**테스트**: 임시 테스트 엔드포인트 `GET /test/gemini?prompt=hello`로 동작 확인

**참고 링크**:

- [Spring AI — Getting Started (BOM 설정)](https://docs.spring.io/spring-ai/reference/getting-started.html)
- [Spring AI — Google GenAI Chat](https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html) —
  ChatModel, ChatOptions, application.yml 프로퍼티
- [Spring AI GitHub — google-genai 모듈 소스](https://github.com/spring-projects/spring-ai/tree/main/models/spring-ai-google-genai)

---

## Step 2: File API — PDF 업로드 (직접 RestClient 구현)

**목표**: S3의 PDF를 다운로드하여 Gemini File API에 업로드하고 file URI를 획득

> Spring AI가 File Upload API를 지원하지 않으므로, 이 Step만 `RestClient`로 직접 구현한다.

**학습 포인트**:

- Gemini File API의 **resumable upload** 프로토콜 (2단계: initiate → upload bytes)
- `X-Goog-Upload-*` 헤더 사용법
- 파일 상태 폴링 (`PROCESSING` → `ACTIVE` 대기)
- `RestClient`로 raw bytes 전송 + 커스텀 헤더 설정 방법
- Spring AI 프로퍼티(`spring.ai.google.genai.api-key`)를 재활용하여 API 키 중복 방지

**생성할 파일**:

| 파일명                               | 위치 (ai 모듈)    | 역할                                                |
|:----------------------------------|:--------------|:--------------------------------------------------|
| `GeminiFileUploadResponse.java`   | `dto/gemini/` | 업로드 응답 매핑 (name, uri, state)                      |
| `GeminiFileService.java`          | `service/`    | `uploadPdf`, `waitForProcessing`, `deleteFile` 구현 |
| `GeminiFileRestClientConfig.java` | `config/`     | File API 전용 RestClient 설정 (Base URL 관리)           |

- `modules/ai/util/PdfUtils.java` — `downloadToTemp(url)` → 임시 파일 Path 획득, `deleteTempFile(path)` →
  정리

**테스트**: 실제 PDF 업로드 → `ACTIVE` 상태 확인 → ChatModel에 fileUri 포함하여 "이 문서를 요약해줘" 호출

**참고 링크**:

- [Gemini API — File API](https://ai.google.dev/gemini-api/docs/files) — Resumable Upload 프로토콜, 파일
  상태 폴링, 삭제
- [Gemini API — File API REST Reference](https://ai.google.dev/api/files) — `media.upload`,
  `files.get`, `files.delete` 엔드포인트 스펙
- [Spring 6 — RestClient](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient) —
  RestClient 사용법

---

## Step 3: Context Caching — GoogleGenAiCachedContentService

**목표**: 업로드된 PDF로 캐시를 생성하여 반복 요청 시 비용 절감

**학습 포인트**:

- Spring AI의 `GoogleGenAiCachedContentService` CRUD 사용법
- Google SDK의 `Content`, `Part`, `FileData` 타입 직접 조립
- `ChatModel.call()` 시 `GoogleGenAiChatOptions.cachedContentName`으로 캐시 참조
- TTL 설정과 만료 관리
- **최소 토큰 제한**: 모델별 상이 (Gemini 2.5 Flash: 1,024 / Gemini 2.5 Pro: 4,096)
- `GoogleGenAiCachedContentService` 빈 등록이 자동 구성에서 실패할 수 있으므로 `GeminiCacheConfig`로 보완

**생성할 파일**:

| 파일명 | 위치 (ai 모듈) | 역할 |
|---|---|---|
| `GeminiCacheService.java` | `service/` | 캐시 생성/삭제 서비스 |
| `GeminiCacheConfig.java` | `config/` | `GoogleGenAiCachedContentService` 빈 등록 보완 |

**캐시 생성 코드**:

```java

@Autowired
GoogleGenAiCachedContentService cacheService;

// File API에서 획득한 fileUri로 Part 구성
Content pdfContent = Content.builder()
    .role("user")
    .parts(List.of(
        Part.builder()
            .fileData(FileData.builder()
                .fileUri(uploadedFileUri)
                .mimeType("application/pdf")
                .build())
            .build()
    ))
    .build();

CachedContentRequest request = CachedContentRequest.builder()
    .model("gemini-2.0-flash")
    .contents(List.of(pdfContent))
    .displayName("quiz-generation-" + UUID.randomUUID())
    .ttl(Duration.ofMinutes(10))
    .build();

GoogleGenAiCachedContent cache = cacheService.create(request);
String cacheName = cache.getName();
```

**캐시 참조하여 질의**:

```java
ChatResponse response = chatModel.call(
    new Prompt("이 문서를 요약해줘",
        GoogleGenAiChatOptions.builder()
            .useCachedContent(true)
            .cachedContentName(cacheName)
            .build()
    ));
```

**캐시 정리**:

```java
cacheService.delete(cacheName);
```

**테스트**: PDF 업로드 → 캐시 생성 → 캐시 참조하여 ChatModel 호출 → 같은 캐시로 2번째 질문 호출 (빠른지 확인)

**참고 링크**:

- [Spring AI — Google GenAI Context Caching](https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html#_context_caching) —
  GoogleGenAiCachedContentService, CachedContentRequest
- [Gemini API — Context Caching](https://ai.google.dev/gemini-api/docs/caching) — CachedContent
  생성/참조/TTL, 최소 토큰 요건
- [Google GenAI Java SDK — Content/Part/FileData](https://github.com/googleapis/java-genai) — SDK 타입
  구조 (캐시에 PDF fileData 전달 시 참조)
- [Spring AI GitHub — CachedContentRequest.java 소스](https://github.com/spring-projects/spring-ai/blob/main/models/spring-ai-google-genai/src/main/java/org/springframework/ai/google/genai/cache/CachedContentRequest.java)

---

## Step 4: 프롬프트 시스템 + Structured Output

**목표**: 프롬프트 템플릿과 JSON 구조 강제를 구현

**학습 포인트**:

- Spring AI의 `BeanOutputConverter` — Java 클래스 → JSON Schema 자동 생성 + 응답 역직렬화
- `GoogleGenAiChatOptions.responseMimeType("application/json")` 설정
- Strategy 패턴 + enum으로 타입별 프롬프트 매핑
- 시스템 프롬프트(캐시에 포함)와 유저 프롬프트(호출마다 전송) 분리

**Structured Output 코드**:

```java
BeanOutputConverter<AIProblemSet> converter = new BeanOutputConverter<>(AIProblemSet.class);
String jsonSchema = converter.getJsonSchema();

// 시스템 프롬프트는 캐시의 systemInstruction에 포함
cacheName = geminiCacheService.createCache(metadata.uri(), strategy, jsonSchema);

// 유저 프롬프트만 호출마다 전송
String userPrompt = UserPrompt.generate(referencePages, quizCount);

ChatResponse response = chatModel.call(
    new Prompt(userPrompt,
        GoogleGenAiChatOptions.builder()
            .useCachedContent(true)
            .cachedContentName(cacheName)
            .responseMimeType("application/json")
            .build()
    ));

AIProblemSet result = converter.convert(response.getResult().getOutput().getText());
```

**생성할 파일**:

| 파일 | 위치 (ai 모듈) | 역할 |
|---|---|---|
| `QuizPromptStrategy.java` | `prompt/quiz/common/` | 프롬프트 전략 인터페이스 (`getFormat()`, `getGuideLine()`) |
| `QuizType.java` | `prompt/quiz/common/` | enum — Strategy 구현 (MULTIPLE, BLANK, OX) |
| `SystemPrompt.java` | `prompt/quiz/system/` | 시스템 프롬프트 조립 — 캐시의 systemInstruction에 포함 |
| `UserPrompt.java` | `prompt/quiz/user/` | 유저 프롬프트 조립 — 호출마다 전송 |
| `MultipleFormat.java` | `prompt/quiz/mutiple/` | 객관식 출력 형식 |
| `MultipleGuideLine.java` | `prompt/quiz/mutiple/` | 객관식 작성 지침 |
| `BlankFormat.java` | `prompt/quiz/blank/` | 빈칸 출력 형식 |
| `BlankGuideLine.java` | `prompt/quiz/blank/` | 빈칸 작성 지침 |
| `OXFormat.java` | `prompt/quiz/ox/` | OX 출력 형식 |
| `OXGuideLine.java` | `prompt/quiz/ox/` | OX 작성 지침 |
| `AIProblemSet.java` | `dto/ai/` | Structured Output 루트 DTO |
| `AIProblem.java` | `dto/ai/` | 개별 문제 DTO |
| `AISelection.java` | `dto/ai/` | 선택지 DTO |
| `ChatOrchestratorService.java` | `service/` | PDF 업로드 → 캐시 → 퀴즈 생성 파이프라인 통합 |

**수정할 파일**:

- `GeminiCacheService.java` — `createCache(fileUri, strategy, jsonSchema)` 시그니처로 변경, `systemInstruction` 추가

**테스트**: 실제 PDF → 캐시 → "객관식 5문제 생성" 프롬프트 → AIProblemSet으로 역직렬화 성공 확인

**참고 링크**:

- [Spring AI — Structured Output Converter](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html) —
  BeanOutputConverter, MapOutputConverter, ListOutputConverter
- [Gemini API — Structured Output (JSON)](https://ai.google.dev/gemini-api/docs/structured-output) —
  responseMimeType, responseSchema

---

## Step 5: 청크 분할 + 병렬 배치 생성

**목표**: 청크 분할 로직을 구현하고, 병렬 Gemini 요청으로 문제 생성

**학습 포인트**:

- Java 21 virtual threads로 병렬 API 호출 (`ExecutorService` +
  `Executors.newVirtualThreadPerTaskExecutor()`)
- `Consumer<AIProblemSet>` 콜백 패턴
- 부분 실패 처리 (N개 중 일부 실패 시 나머지는 성공 처리)
- **같은 `ChatModel` 인스턴스를 N개 스레드에서 동시 호출** (Spring AI의 thread-safety 확인)

**생성할 파일**:

| 파일 | 위치 (ai 모듈) | 역할 |
|---|---|---|
| `ChunkSplitter.java` | `util/` | `createPageChunks(pageNumbers, quizCount, maxChunkCount)` |
| `ChunkInfo.java` | `dto/` | 청크 정보 (referencedPages, quizCount) |
| `GeminiQuizOrchestrator.java` | `service/` | 전체 파이프라인 조율 |

**파이프라인 흐름**:

```
GeminiFileService.uploadPdf(fileUrl)              [RestClient 직접 구현]
  → FileMetadata { name, uri }
  → BeanOutputConverter.getJsonSchema()           [Spring AI]
  → GeminiCacheService.createCache(uri, strategy, jsonSchema)  [Spring AI]
    → SystemPrompt.generate(strategy, jsonSchema) → systemInstruction
    → PDF + 시스템 프롬프트 + JSON Schema 캐시에 포함
  → ChunkSplitter.createPageChunks(...)
  → [Virtual Thread Pool] 각 청크마다:
      UserPrompt.generate(chunk.referencedPages, chunk.quizCount)
      → chatModel.call(userPrompt, options { cacheName, "application/json" })
      → BeanOutputConverter.convert() → AIProblemSet
      → 검증 (선택지 4개 초과 → 폐기)
      → 선택지 셔플 (MULTIPLE/BLANK)
      → 번호 재할당 (AtomicInteger)
      → Consumer<AIProblemSet> 콜백 호출
  → finally: cacheService.deleteCache(cacheName) + fileService.deleteFile(name)
```

**테스트**: quizCount=15 → N개 콜백 수신, 문제 번호 1-15 순차 확인, 순차 실행 대비 속도 비교

**참고 링크**:

- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444) — Java 21 Virtual Threads 공식 스펙
- [Executors.newVirtualThreadPerTaskExecutor() Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html#newVirtualThreadPerTaskExecutor())

---

## Step 6: Rate Limiter + 에러 처리 + Circuit Breaker

**목표**: 청크 기반 Rate Limiter를 구현하고, 운영 환경에 맞는 장애 대응 추가

### 6-1. Rate Limiter — Bucket4j 기반 청크 단위 토큰 버킷

**핵심 특징**:

- **요청(request) 단위가 아니라 청크(chunk) 단위**로 카운트 (quizCount=20 → 청크 10개 → 10 소모)
- AI API 호출 **전에** 사전 검증하여, 한도 초과 시 요청 자체를 거부

**Resilience4j `@RateLimiter`로 대체할 수 없는 이유**:

> Resilience4j `@RateLimiter`는 **메서드 호출 1회 = 1 소모** 고정이다.
> 퀴즈 생성은 **1회 호출에 N개 청크를 소모**하는 가변 비용 구조이다.
> 따라서 `tryConsume(N)`을 지원하는 **Bucket4j**를 사용한다.

**Bucket4j 선택 근거**:

- `tryConsume(chunkCount)` — 가변 비용 소모 지원
- thread-safe 보장 (직접 구현 시 `synchronized` 관리 불필요)
- 나중에 서버 다중화 시 `bucket4j-redis`로 분산 Rate Limit 확장 가능

**의존성 추가** (`modules/ai/build.gradle`):

```groovy
implementation "com.bucket4j:bucket4j_jdk17-core:8.14.0"
```

**구현 방향**:

```java

@Component
public class ChunkRateLimiter {

    private final Bucket bucket;

    public ChunkRateLimiter(
        @Value("${gemini.rate-limiter.limit:75}") int limit,
        @Value("${gemini.rate-limiter.window-seconds:60}") int windowSeconds
    ) {
        this.bucket = Bucket.builder()
            .addLimit(bandwidthLimit -> bandwidthLimit
                .capacity(limit)
                .refillGreedy(limit, Duration.ofSeconds(windowSeconds)))
            .build();
    }

    /**
     * AI API 호출 전에 호출. 청크 수만큼 토큰을 소모한다.
     * 한도 초과 시 CustomException(AI_SERVER_TO_MANY_REQUEST)를 던진다.
     */
    public void acquire(int chunkCount) {
        if (!bucket.tryConsume(chunkCount)) {
            throw new CustomException(ExceptionMessage.AI_SERVER_TO_MANY_REQUEST);
        }
    }
}
```

**생성할 파일**:

| 파일 | 위치 (ai 모듈) | 역할 |
|---|---|---|
| `ChunkRateLimiter.java` | `service/` | Bucket4j 기반 청크 단위 Rate Limiter |

**파이프라인 내 호출 위치** (Step 5 `GeminiQuizOrchestrator`에서):

```
ChunkSplitter.createPageChunks(...)
  → chunkRateLimiter.acquire(chunks.size())   ← 여기서 사전 검증
  → [병렬] chatModel.call(...) x N
```

### 6-2. 에러 처리 + Circuit Breaker

**학습 포인트**:

- Resilience4j `@CircuitBreaker` + `fallbackMethod` 패턴
- **Spring AI 예외 타입** 파악 (`NonTransientAiException`, `TransientAiException` 등)
- Spring AI 예외 → 기존 `ExceptionMessage` 매핑
- 라이브러리 모듈에서 AOP 프록시가 동작하려면 `starter-aop` 필요
- temp file/cache 정리의 `finally` 보장
  ㅇ
  **수정할 파일**:

- `modules/ai/build.gradle` — `resilience4j-spring-boot3`, `starter-aop` 추가
- `GeminiQuizOrchestrator.java` — `@CircuitBreaker` 어노테이션 + fallback 메서드
- `application-local.yml` — circuit breaker 인스턴스 설정 + rate limiter 설정
  ```yaml
  gemini:
    rate-limiter:
      window-seconds: 60
      limit: 75
  ```

**에러 매핑**:

| 에러 상황 | Spring AI 예외 | ExceptionMessage |
|---|---|---|
| 429 RESOURCE_EXHAUSTED | `TransientAiException` | `AI_SERVER_TO_MANY_REQUEST` |
| 400 INVALID_ARGUMENT | `NonTransientAiException` | `ClientSideException` |
| 500 INTERNAL | `TransientAiException` | `AI_SERVER_RESPONSE_ERROR` |
| 연결 타임아웃 | `TransientAiException` | `AI_SERVER_TIMEOUT` |
| 응답 JSON 파싱 실패 | `OutputConversionException` | `INVALID_AI_RESPONSE` |
| 응답 빈 텍스트 | 직접 체크 | `NULL_AI_RESPONSE` |
| 청크 Rate Limit 초과 | `CustomException` | `AI_SERVER_TO_MANY_REQUEST` |

**참고할 기존 패턴**:

- `modules/quiz/impl/adapter/AIServerAdapter.java` — CircuitBreaker + fallback 전체 패턴
- `modules/global/error/ExceptionMessage.java` — AI 관련 에러 코드 목록

**테스트**:

- Rate Limiter: 한도 75에서 청크 80개 요청 → 429 거부 확인, 60초 대기 후 재시도 → 성공 확인
- Circuit Breaker: 잘못된 API 키로 3회 호출 → circuit OPEN 확인

**참고 링크**:

- [Bucket4j GitHub](https://github.com/bucket4j/bucket4j) — 토큰 버킷 알고리즘 개요
- [Bucket4j Reference — Basic Usage](https://bucket4j.com/8.14.0/toc.html) — Bucket.builder(),
  tryConsume(N), Bandwidth 설정
- [Maven Central — bucket4j_jdk17-core](https://central.sonatype.com/artifact/com.bucket4j/bucket4j_jdk17-core) —
  의존성 좌표/최신 버전
- [Resilience4j — CircuitBreaker](https://resilience4j.readme.io/docs/circuitbreaker) — 상태 전이, 설정
  파라미터
- [Resilience4j — Spring Boot 3 통합](https://resilience4j.readme.io/docs/getting-started-3) — 어노테이션
  기반 설정

---

## Step 7: 기존 시스템 통합

**목표**: `GenerationServiceImpl`에서 `GeminiQuizOrchestrator` 호출하도록 연결

**핵심 변경**:

- `GenerationServiceImpl.java`의 virtual thread 내부에서 `geminiQuizOrchestrator.generateQuizzes()` 호출
- DTO 브리지: `AIProblemSet` → `ProblemSetGeneratedEvent` 매퍼 작성 (기존 `doMainLogic` 유지를 위해)
- `doMainLogic`은 `ProblemSetGeneratedEvent`를 받아 `Problem` 엔티티로 변환 + DB 저장 + SSE 전송하므로 그대로 유지

**수정할 핵심 파일**:

- `modules/quiz/impl/service/GenerationServiceImpl.java` — `GeminiQuizOrchestrator` 연결
- `modules/quiz/impl/build.gradle` — `implementation project(":ai")` 추가

**생성할 파일**:

| 파일 | 위치 (quiz-impl) | 역할 |
|---|---|---|
| `AIProblemSetToEventMapper.java` | `mapper/` | AIProblemSet → ProblemSetGeneratedEvent 변환 |

**매퍼 구현 참고**:

> `AIProblemSet` (ai 모듈): `quiz: List<AIProblem>` (number, title, selections, explanation)
> `ProblemSetGeneratedEvent` (quiz-api 모듈): `quiz: List<QuizGeneratedFromAI>` (number, title,
> selections, explanation)
>
> 구조가 거의 동일하므로 단순 필드 복사 매핑이다.

**테스트**: 전체 E2E — PDF 업로드 → POST /generation → SSE 이벤트 수신 → DB 저장 확인 → Slack 알림 확인

**참고 링크**:

- 기존 코드: `modules/quiz/impl/service/GenerationServiceImpl.java` — virtual thread + Consumer 콜백 패턴
- 기존 코드: `modules/quiz/impl/adapter/AIServerAdapter.java` — 교체 대상 어댑터

---

## Step 8: 정리 + 운영 최적화

**제거 대상**:

- `modules/quiz/impl/adapter/AIServerAdapter.java` — AI 서버 통신 어댑터
- `modules/quiz/impl/config/RestClientConfig.java` — AI 서버용 RestClient 빈 (Gemini File API용은 ai 모듈에
  별도 존재)
- `modules/quiz/impl/mapper/FeRequestToAIRequestMapper.java` — FE → AI 요청 변환
- `modules/quiz/api/dto/aiRequest/GenerationRequestToAI.java` — AI 서버 요청 DTO
- `modules/quiz/api/dto/aiResponse/StreamEvent.java`, `ErrorEvent.java`,
  `ProblemSetGeneratedEvent.java` — NDJSON 스트림 DTO (Gemini에서는 불필요)

**설정 정리**:

- `application-local.yml`에서 `spring.ai.openai` 설정 블록 제거
- `application-local.yml`에서 `q-asker.web.ai-server-url`, `ai-mocking-server-url` 제거
- `QAskerProperties.java`에서 `aiServerUrl`, `aiMockingServerUrl` 필드 제거

**운영 최적화**:

- Gemini 토큰 사용량 로깅 (`ChatResponse.getMetadata()` → `usageMetadata`)
- API 키를 환경 변수로 관리 (`${GEMINI_API_KEY}`)
- 캐시 TTL 전략 최적화 (생성 예상 시간 + 여유분)
- 동시 사용자 대비 Resilience4j `@RateLimiter` 고려

**참고 링크**:

- [Gemini API — 모델 목록 & 제한](https://ai.google.dev/gemini-api/docs/models/gemini) — 모델별 RPM/TPM 제한,
  가격
- [Spring AI — Google GenAI Chat Properties](https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html#_auto_configuration) —
  application.yml 프로퍼티 전체 목록

---

## 현재 코드 상태 요약

> Step 1~4 구현 완료. Step 5 이후는 미구현.

### ai 모듈 (`modules/ai`) — 현재 존재하는 파일

| 파일 | 상태 |
|---|---|
| `config/GeminiFileRestClientConfig.java` | 완료 (Step 2) |
| `config/GeminiCacheConfig.java` | 완료 (Step 3) |
| `dto/GeminiFileUploadResponse.java` | 완료 (Step 2) |
| `dto/ai/AIProblemSet.java` | 완료 (Step 4) |
| `dto/ai/AIProblem.java` | 완료 (Step 4) |
| `dto/ai/AISelection.java` | 완료 (Step 4) |
| `prompt/quiz/common/QuizPromptStrategy.java` | 완료 (Step 4) |
| `prompt/quiz/common/QuizType.java` | 완료 (Step 4) |
| `prompt/quiz/system/SystemPrompt.java` | 완료 (Step 4) |
| `prompt/quiz/user/UserPrompt.java` | 완료 (Step 4) |
| `prompt/quiz/mutiple/MultipleFormat.java` | 완료 (Step 4) |
| `prompt/quiz/mutiple/MultipleGuideLine.java` | 완료 (Step 4) |
| `prompt/quiz/blank/BlankFormat.java` | 완료 (Step 4) |
| `prompt/quiz/blank/BlankGuideLine.java` | 완료 (Step 4) |
| `prompt/quiz/ox/OXFormat.java` | 완료 (Step 4) |
| `prompt/quiz/ox/OXGuideLine.java` | 완료 (Step 4) |
| `service/GeminiFileService.java` | 완료 (Step 2) |
| `service/GeminiCacheService.java` | 완료 (Step 3+4) |
| `service/ChatOrchestratorService.java` | 완료 (Step 4 — 단일 호출 파이프라인) |
| `util/PdfUtils.java` | 완료 (Step 2) |

### Step 5 이후 생성 예정 파일

| 파일 | 위치 (ai 모듈) | Step |
|---|---|---|
| `ChunkSplitter.java` | `util/` | 5 |
| `ChunkInfo.java` | `dto/` | 5 |
| `GeminiQuizOrchestrator.java` | `service/` | 5 |
| `ChunkRateLimiter.java` | `service/` | 6 |

### quiz-impl 모듈 — 교체 대상 파일

| 파일                                       | 역할                     | 처리              |
|------------------------------------------|------------------------|-----------------|
| `adapter/AIServerAdapter.java`           | AI 서버 NDJSON 스트림 클라이언트 | **제거** (Step 8) |
| `config/RestClientConfig.java`           | AI 서버용 RestClient 빈 2개 | **제거** (Step 8) |
| `mapper/FeRequestToAIRequestMapper.java` | FE → AI 요청 변환          | **제거** (Step 8) |
| `service/GenerationServiceImpl.java`     | 핵심 서비스 로직              | **수정** (Step 7) |

### Spring AI가 대체하는 것 vs 직접 구현하는 것

| 영역                          | 방식                                                       | 비고                                             |
|-----------------------------|----------------------------------------------------------|------------------------------------------------|
| Gemini 호출 (generateContent) | **Spring AI** `ChatModel`                                | DTO 보일러플레이트 제거                                 |
| Context Caching CRUD        | **Spring AI** `GoogleGenAiCachedContentService`          | create/delete/list 내장                          |
| Structured Output           | **Spring AI** `BeanOutputConverter` + `responseMimeType` | JSON Schema 자동 생성                              |
| File Upload API             | **RestClient 직접 구현**                                     | Spring AI 미지원                                  |
| 청크 분할                       | **직접 구현** (`ChunkSplitter`)                              |                                                |
| 프롬프트 조립                     | **직접 구현** (`SystemPrompt`, `UserPrompt`, `QuizType`)     | Strategy 패턴 + 타입별 상수 클래스                       |
| 파이프라인 오케스트레이션               | **직접 구현** (`GeminiQuizOrchestrator`)                     | 비즈니스 로직                                        |
| Rate Limiter (청크 단위)        | **Bucket4j** (`ChunkRateLimiter`)                        | `tryConsume(N)` 가변 비용 소모 (Resilience4j로 대체 불가) |
| Circuit Breaker             | **Resilience4j**                                         | 기존 패턴 재활용                                      |

---

## 학습으로 얻는 것

### Spring AI 프레임워크

- `ChatModel` / `ChatClient` 추상화와 프로바이더 교체 구조
- `BeanOutputConverter`로 LLM 응답 → Java 객체 자동 변환
- `GoogleGenAiCachedContentService`로 Context Caching 관리
- `GoogleGenAiChatOptions`로 요청별 옵션 동적 설정
- Spring AI BOM 도입과 멀티모듈 프로젝트에서의 의존성 관리

### API 통신 계층

- `RestClient`로 Gemini File API 직접 호출 (resumable upload + 커스텀 헤더)
- 상태 폴링 패턴 (PROCESSING → ACTIVE 대기)
- Spring AI와 직접 REST 호출의 하이브리드 구조 설계

### 설계/아키텍처

- Consumer 콜백 패턴 — 비동기 결과를 호출자에게 전달하는 구조
- 파이프라인 오케스트레이션 — 다운로드 → 업로드 → 캐시 → 병렬생성 → 정리를 하나의 흐름으로 조율
- DTO 브리지 매퍼 (모듈 간 의존성을 끊으면서 데이터 전달)

### 동시성

- Java 21 Virtual Threads로 병렬 API 호출
- 부분 실패 처리 (N개 중 일부 실패해도 나머지 성공 처리)
- `AtomicBoolean` + SSE 취소 처리

### 장애 대응 + Rate Limiting

- Bucket4j 기반 청크 단위 Rate Limiter (Resilience4j로 대체 불가한 가변 비용 구조 → `tryConsume(N)`)
- Resilience4j CircuitBreaker — 설정, fallback, 상태 전이 (CLOSED → OPEN → HALF_OPEN)
- Spring AI 예외 타입 → 도메인 예외 매핑
- `finally` 블록으로 외부 리소스(캐시, 임시파일) 정리 보장

### AI/LLM 엔지니어링

- Structured Output — `BeanOutputConverter` + `responseMimeType` 으로 LLM 응답 형태 강제
- 퀴즈 타입별 프롬프트 템플릿 설계 (system message 조립)
- Context Caching — 같은 문서에 대한 반복 질의 비용 절감 구조
- 청크 분할 전략 (페이지 + 문제수 분배)

### 실무 감각

- 기존 시스템에 새 구현을 비파괴적으로 교체하는 과정 (Step 7에서 끼워넣고, Step 8에서 정리)
- 프레임워크 미지원 기능의 하이브리드 해결 경험

---

## 검증 방법

1. **Step별 단위 테스트**: 각 Step 완료 시 해당 기능만 독립 테스트
2. **통합 테스트**: Step 7 완료 후 전체 흐름 E2E 테스트
    - `POST /generation` with 실제 PDF URL → SSE 스트림 수신 → DB 확인
3. **장애 테스트**: Step 6 완료 후 Circuit Breaker 동작 확인
4. **성능 비교**: Step 5에서 순차 vs 병렬 소요 시간 측정

---

## 이력서 작성 예시

> 아래 항목들은 이력서/포트폴리오의 프로젝트 경험 란에 각각 독립된 소주제로 작성할 수 있다.

### 1. Spring AI + Gemini API 기반 퀴즈 생성 파이프라인

> Spring AI + Gemini API 기반으로 퀴즈 생성 파이프라인을 설계하고 구현했습니다. 기존 시스템의
> Consumer 콜백 인터페이스를 유지한 채 내부 구현만 교체하는 비파괴적 전환을 수행했습니다.

### 2. PDF Context Caching으로 LLM API 비용 절감

> Gemini File API로 PDF를 업로드한 뒤 Context Caching을 적용하여, N개 병렬 청크 요청이 동일 캐시를 참조하도록 설계했습니다. 매 요청마다 PDF
> 토큰을 반복 전송하던 기존 방식 대비 입력 토큰 비용을 대폭 절감했습니다.

### 3. 청크 분할 + Virtual Threads 병렬 생성

> 문제 수와 페이지를 N개 청크로 균등 분할한 뒤, Java 21 Virtual Threads로 Gemini API를 병렬 호출하여 생성 속도를 향상시켰습니다. 부분 실패 시에도
> 성공한 청크의 결과는 정상 처리되도록 설계하여 사용자 경험을 보장했습니다.

### 4. Bucket4j 기반 가변 비용 Rate Limiting

> 요청 1건의 비용이 청크 수에 따라 달라지는 구조에서 Resilience4j의 고정 비용 Rate Limiter로는 대응할 수 없었습니다. Bucket4j의
`tryConsume(N)` API를 활용하여 청크 단위 토큰 버킷을 구현하고, API 호출 전에 사전 검증하여 한도 초과 요청을 차단했습니다.

### 5. Spring AI로 LLM 통합 — Structured Output과 프로바이더 추상화

> Spring AI의 ChatModel 추상화로 Gemini API를 통합하고, BeanOutputConverter + responseMimeType으로 LLM 응답을 Java
> 객체로 직접 역직렬화하는 Structured Output 파이프라인을 구축했습니다. 프레임워크가 미지원하는 File Upload API는 GitHub 소스 코드를 분석하여 확장
> 가능성을 검증한 뒤, RestClient로 직접 구현하는 하이브리드 구조를 설계했습니다.

### 기술 키워드

`Spring AI` `Gemini API` `Context Caching` `Structured Output` `Virtual Threads` `Bucket4j`
`Resilience4j` `RestClient` `멀티모듈 Gradle`

