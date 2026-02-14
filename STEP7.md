# Step 7: Rate Limiter + 에러 처리 + Circuit Breaker

> Bucket4j 기반 청크 단위 Rate Limiter를 구현하고,
> Spring AI 예외를 도메인 예외로 매핑하며,
> Resilience4j Circuit Breaker를 ai 모듈에 적용한다.

---

## 전체 플로우

```
Client → POST /generation
  │
  ▼
GenerationServiceImpl (Virtual Thread)
  │
  ├── CircuitBreaker 상태 확인 (OPEN → 즉시 에러 반환)
  │
  ▼
AIServerAdapter.streamRequest()
  │
  ├── @CircuitBreaker(name = "aiServer", fallbackMethod = "fallback")
  │
  ├── 1. ChunkSplitter.createPageChunks(pages, count, MAX)
  │
  ├── 2. chunkRateLimiter.acquire(chunks.size())   ← NEW (사전 검증)
  │     └ 한도 초과 시 AI_SERVER_TO_MANY_REQUEST 즉시 throw
  │
  ├── 3. orchestrationService.generateQuizzes(...)
  │     │
  │     ├── GeminiFileService.uploadPdf()
  │     ├── GeminiCacheService.createCache()
  │     │
  │     ├── [Virtual Thread Pool + Semaphore]
  │     │     ├ chatModel.call()          ← Spring AI 예외 발생 가능
  │     │     ├ converter.convert()       ← 파싱 예외 발생 가능
  │     │     ├ 검증 + 셔플 + 번호 할당
  │     │     └ Consumer 콜백
  │     │
  │     └── finally: 캐시 삭제 + 파일 삭제
  │
  └── 4. fallback() — 예외 타입별 도메인 예외 매핑 ← 수정
```

---

## 핵심 설계 원칙

### Rate Limiter는 API 호출 전에 사전 검증

```
Rate Limiter의 목적: Gemini API RPM(분당 요청 수) 한도를 초과하지 않도록 사전 차단.

API 호출을 시작하기 전에 acquire()로 토큰을 소모한다.
한도가 부족하면 PDF 업로드, 캐시 생성 등 비용이 드는 작업을 시작하지 않고 즉시 거부한다.

→ "비싼 작업을 시작한 뒤 실패" 대신 "시작 전에 빠르게 실패"
```

### Resilience4j @RateLimiter로 대체할 수 없는 이유

```
Resilience4j @RateLimiter는 메서드 호출 1회 = 1 소모 고정이다.

퀴즈 생성은 quizCount에 따라 청크 수가 달라진다:
  quizCount=5  → 청크 5개 → Gemini API 5회 호출
  quizCount=20 → 청크 10개 → Gemini API 10회 호출

같은 메서드 1회 호출이지만 API 호출 횟수는 5배 차이.
→ tryConsume(N)으로 가변 비용을 소모할 수 있는 Bucket4j를 사용한다.
```

### 에러 처리는 두 계층으로 분리

```
계층 1: 청크 내부 (processChunk) — 부분 실패 허용
  ├ chatModel.call() 실패 → log + failureCount++ → 다른 청크는 계속 진행
  ├ converter.convert() 실패 → log + failureCount++ → 다른 청크는 계속 진행
  └ 전체 청크 실패 → RuntimeException throw → 계층 2로 전파

계층 2: AIServerAdapter.fallback() — 예외 타입별 도메인 예외 매핑
  ├ CallNotPermittedException → Circuit OPEN → AI_SERVER_COMMUNICATION_ERROR
  ├ CustomException(AI_SERVER_TO_MANY_REQUEST) → Rate Limit 초과 → 그대로 전파
  ├ Spring AI 예외 → 타입별 매핑 (아래 에러 매핑표 참조)
  └ 기타 예외 → AI_SERVER_COMMUNICATION_ERROR
```

---

## 1단계: Bucket4j 의존성 추가

**경로**: `modules/ai/impl/build.gradle`

```groovy
dependencies {
    // ... 기존 의존성 ...

    // ────────────────────────────────
    // Rate Limiter
    // ────────────────────────────────
    // Bucket4j: 토큰 버킷 알고리즘 기반 Rate Limiter
    // 예: bucket.tryConsume(chunkCount) → 청크 수만큼 토큰 소모,
    //     한도 초과 시 false 반환 → 요청 거부
    implementation "com.bucket4j:bucket4j_jdk17-core:8.14.0"
}
```

> Bucket4j는 ai-impl 모듈에 추가한다.
> `ChunkRateLimiter`는 ai-impl 내부에 위치하며, quiz-impl에서 직접 참조하지 않는다.

---

## 2단계: `ChunkRateLimiter.java` — 청크 단위 Rate Limiter

**경로**: `modules/ai/impl/src/main/java/com/icc/qasker/ai/service/ChunkRateLimiter.java`

```java
package com.icc.qasker.ai.service;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import io.bucket4j.Bucket;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
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

        log.info("ChunkRateLimiter 초기화: limit={}, windowSeconds={}", limit, windowSeconds);
    }

    /**
     * AI API 호출 전에 호출. 청크 수만큼 토큰을 소모한다.
     * 한도 초과 시 CustomException(AI_SERVER_TO_MANY_REQUEST)를 던진다.
     *
     * @param chunkCount 소모할 토큰 수 (= 청크 수 = Gemini API 호출 횟수)
     */
    public void acquire(int chunkCount) {
        if (!bucket.tryConsume(chunkCount)) {
            log.warn("Rate Limit 초과: 요청 청크 수={}, 잔여 토큰={}",
                chunkCount, bucket.getAvailableTokens());
            throw new CustomException(ExceptionMessage.AI_SERVER_TO_MANY_REQUEST);
        }
        log.debug("Rate Limit 통과: 소모={}, 잔여={}",
            chunkCount, bucket.getAvailableTokens());
    }
}
```

### Bucket4j 토큰 버킷 동작

```
초기 상태: 75개 토큰 (capacity)
1분마다 75개로 리필 (refillGreedy)

시간 ─────────────────────────────────────────────────>

요청 A: quizCount=20 → 청크 10개 → tryConsume(10) → 잔여 65 ✅
요청 B: quizCount=15 → 청크 10개 → tryConsume(10) → 잔여 55 ✅
요청 C: quizCount=20 → 청크 10개 → tryConsume(10) → 잔여 45 ✅
   ...
요청 H: quizCount=20 → 청크 10개 → tryConsume(10) → 잔여 -5 ❌
  → CustomException(AI_SERVER_TO_MANY_REQUEST)
  → "서버가 생성요청 한도에 도달했습니다. 문제 개수를 줄이거나 1분 뒤 다시 시도해주세요."

  (1분 경과 → 75개로 리필)

요청 I: quizCount=20 → 청크 10개 → tryConsume(10) → 잔여 65 ✅
```

### 왜 `refillGreedy`인가?

```
refillGreedy: 윈도우 기간 동안 균등하게 토큰을 채운다.
  → 1분 = 60초, 75개 → 약 0.8초마다 1개씩 채워짐
  → 부드러운 속도 제한

refillIntervally: 윈도우 끝에 한 번에 모두 채운다.
  → 59초에 0개 → 60초에 75개 → 버스트 허용
  → Gemini API RPM 제한에는 부적합
```

---

## 3단계: `QuizOrchestrationServiceImpl` 수정 — Rate Limiter 연동

**경로**: `modules/ai/impl/src/main/java/com/icc/qasker/ai/service/QuizOrchestrationServiceImpl.java`

### 변경 사항

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizOrchestrationServiceImpl implements QuizOrchestrationService {

    private static final int MAX_CHUNK_COUNT = 10;
    private static final int MAX_SELECTION_COUNT = 4;
    private static final int MAX_CONCURRENT_CALLS = 5;

    private final GeminiFileService geminiFileService;
    private final GeminiCacheService geminiCacheService;
    private final ChatModel chatModel;
    private final ChunkRateLimiter chunkRateLimiter;  // ← NEW

    @Override
    public void generateQuiz(
        String fileUrl,
        String strategyValue,
        int quizCount,
        List<Integer> referencePages,
        Consumer<AIProblemSet> onChunkCompleted
    ) {
        List<ChunkInfo> chunks = ChunkSplitter.createPageChunks(
            referencePages, quizCount, MAX_CHUNK_COUNT
        );
        log.info("청크 분할 완료: {}개 청크", chunks.size());

        // ──── Rate Limit 사전 검증 (비싼 작업 전에 차단) ────
        chunkRateLimiter.acquire(chunks.size());  // ← NEW

        // ──── PDF 업로드 + 캐시 생성 ────
        FileMetadata metadata = geminiFileService.uploadPdf(fileUrl);
        log.info("업로드 완료: name={}, uri={}", metadata.name(), metadata.uri());

        // ... 이하 기존 코드 동일 ...
    }
}
```

### Rate Limiter 호출 위치가 중요한 이유

```
변경 전 순서:
  1. uploadPdf()      ← ~5초 소요, 비용 발생
  2. createCache()    ← ~2초 소요, 비용 발생
  3. 병렬 호출

변경 후 순서:
  1. createPageChunks()    ← 즉시 (메모리 연산)
  2. acquire(chunks.size())  ← 즉시 (토큰 확인) ← 여기서 거부 가능
  3. uploadPdf()           ← 통과 시에만 실행
  4. createCache()
  5. 병렬 호출

→ 한도 초과 시 PDF 업로드/캐시 생성 비용 없이 즉시 거부
```

---

## 4단계: `AIServerAdapter` 수정 — 에러 매핑 강화

**경로**: `modules/quiz/impl/src/main/java/com/icc/qasker/quiz/adapter/AIServerAdapter.java`

### 에러 매핑표

| 에러 상황 | 예외 타입 | ExceptionMessage | HTTP |
|---|---|---|---|
| Rate Limit 초과 (Bucket4j) | `CustomException` | `AI_SERVER_TO_MANY_REQUEST` | 429 |
| Circuit OPEN | `CallNotPermittedException` | `AI_SERVER_COMMUNICATION_ERROR` | 500 |
| 429 RESOURCE_EXHAUSTED | `NonTransientAiException` | `AI_SERVER_TO_MANY_REQUEST` | 429 |
| 400 INVALID_ARGUMENT | `NonTransientAiException` | `AI_SERVER_RESPONSE_ERROR` | 500 |
| 500 INTERNAL | `TransientAiException` | `AI_SERVER_RESPONSE_ERROR` | 500 |
| 연결 타임아웃 | `ResourceAccessException` | `AI_SERVER_COMMUNICATION_ERROR` | 500 |
| 응답 JSON 파싱 실패 | `ConversionFailedException` | `INVALID_AI_RESPONSE` | 422 |
| 응답 빈 텍스트 | `IllegalStateException` | `NULL_AI_RESPONSE` | 500 |
| 전체 청크 실패 | `RuntimeException` | `AI_SERVER_RESPONSE_ERROR` | 500 |
| 사용자 측 오류 | `ClientSideException` | — (무시) | — |

### 수정된 코드

```java
package com.icc.qasker.quiz.adapter;

import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.global.error.ClientSideException;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.aiRequest.GenerationRequestToAI;
import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Component
@AllArgsConstructor
public class AIServerAdapter {

    private final QuizOrchestrationService quizOrchestrationService;

    @CircuitBreaker(name = "aiServer", fallbackMethod = "fallback")
    public void streamRequest(
        GenerationRequestToAI request,
        Consumer<ProblemSetGeneratedEvent> onLineReceived
    ) {
        quizOrchestrationService.generateQuiz(
            request.uploadedUrl(),
            request.quizType().name(),
            request.quizCount(),
            request.pageNumbers(),
            (problemSet) -> {
                // AIProblemSet → ProblemSetGeneratedEvent 매핑
                ProblemSetGeneratedEvent event = mapToEvent(problemSet);
                onLineReceived.accept(event);
            }
        );
    }

    private void fallback(GenerationRequestToAI request,
        Consumer<ProblemSetGeneratedEvent> onLineReceived,
        Throwable t) {

        // 1. Circuit Breaker OPEN — 서버 보호 모드
        if (t instanceof CallNotPermittedException) {
            log.error("[CircuitBreaker] AI 서버 요청 차단됨 (Circuit Open): {}",
                t.getMessage());
            throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
        }

        // 2. Rate Limit 초과 — CustomException 그대로 전파
        if (t instanceof CustomException ce
            && ce.getHttpStatus().value() == 429) {
            log.warn("[RateLimiter] 요청 거부: {}", t.getMessage());
            throw ce;
        }

        // 3. Gemini API 429 RESOURCE_EXHAUSTED
        if (t instanceof NonTransientAiException
            && t.getMessage() != null
            && t.getMessage().contains("RESOURCE_EXHAUSTED")) {
            log.error("[Gemini] 429 Rate Limit: {}", t.getMessage());
            throw new CustomException(ExceptionMessage.AI_SERVER_TO_MANY_REQUEST);
        }

        // 4. 연결 타임아웃/실패
        if (t instanceof ResourceAccessException) {
            log.error("[Network] AI 서버 연결 시간 초과/실패: {}", t.getMessage());
            throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
        }

        // 5. 사용자 측 오류 — Circuit Breaker에 기록하지 않음
        if (t instanceof ClientSideException) {
            log.error("[Client] 사용자 오류 발생: {}", t.getMessage());
            return;
        }

        // 6. 기타 예외
        log.error("[Unknown] AI Server Error: {}", t.getMessage(), t);
        throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
    }
}
```

### `@CircuitBreaker`와 `fallback`의 동작

```
streamRequest() 호출
  │
  ├── 정상 → 결과 반환
  │
  └── 예외 발생
        │
        ├── Circuit CLOSED/HALF_OPEN → fallback() 호출 + 실패 기록
        │     └ fallback에서 CustomException throw → GenerationServiceImpl.finalizeError()
        │
        └── Circuit OPEN → CallNotPermittedException 즉시 발생
              └ fallback에서 AI_SERVER_COMMUNICATION_ERROR throw

ignore-exceptions에 ClientSideException이 등록되어 있으므로
사용자 오류는 Circuit Breaker 실패 카운트에 포함되지 않는다.
```

---

## 5단계: Resilience4j 설정 확인

**경로**: `app/src/main/resources/application-local.yml`

기존 설정이 이미 적절하게 구성되어 있다. 확인만 한다:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      aiServer:
        sliding-window-type: COUNT_BASED
        # 슬라이딩 윈도우에 최소 3개는 채우고 판단한다
        minimum-number-of-calls: 3
        # 슬라이딩 윈도우: 최근 10개의 요청을 기준으로 판단한다.
        sliding-window-size: 10
        # 어떤 예외를 비정상으로 간주할 것인가?
        ignore-exceptions:
          - com.icc.qasker.global.error.ClientSideException
        # slidingWindowSize 중 몇 %가 recordException이면 OPEN으로 만들 것인가?
        failure-rate-threshold: 60
        # OPEN 상태에서 HALF_OPEN으로 가려면 얼마나 기다릴 것인가?
        wait-duration-in-open-state:
          seconds: 10
        # HALF_OPEN에서 허용할 호출 수, 기준 비율은 CLOSED->OPEN 시와 동일
        permitted-number-of-calls-in-half-open-state: 2
        # OPEN 상태에서 자동으로 HALF_OPEN으로 갈 것인가?
        automatic-transition-from-open-to-half-open-enabled: true
        # actuator를 위한 이벤트 버퍼 사이즈
        event-consumer-buffer-size: 10
    metrics:
      enabled: true
```

### Rate Limiter 설정 추가

```yaml
# application-local.yml에 추가
gemini:
  rate-limiter:
    limit: 75
    window-seconds: 60
```

### Circuit Breaker 상태 전이

```
정상 운영                연속 실패               복구 확인
────────          ────────────────        ────────────
 CLOSED    ──→      OPEN         ──→     HALF_OPEN
                (10개 중 60% 실패)     (10초 대기 후 자동 전이)
    ↑                                       │
    │                                       │
    └───────────────────────────────────────┘
               (2개 호출 중 성공률 > 40%)
```

---

## 6단계: `QuizOrchestrationServiceImpl` 에러 처리 강화

### processChunk 내부 에러 처리 (기존 코드에서 보강)

```java
private void processChunk(
    ChunkInfo chunk,
    String cacheName,
    BeanOutputConverter<GeminiProblemSet> converter,
    String strategyValue,
    AtomicInteger numberCounter,
    Consumer<AIProblemSet> onChunkCompleted
) {
    try {
        log.debug("청크 처리 시작: pages={}, quizCount={}",
            chunk.referencedPages(), chunk.quizCount());

        String userPrompt = UserPrompt.generate(
            chunk.referencedPages(), chunk.quizCount()
        );

        ChatResponse response = chatModel.call(
            new Prompt(userPrompt,
                GoogleGenAiChatOptions.builder()
                    .useCachedContent(true)
                    .cachedContentName(cacheName)
                    .responseMimeType("application/json")
                    .build())
        );

        String jsonText = response.getResult().getOutput().getText();

        // ──── 빈 응답 체크 ────
        if (jsonText == null || jsonText.isBlank()) {
            throw new IllegalStateException(
                "Gemini 응답이 비어있음: pages=" + chunk.referencedPages()
            );
        }

        log.debug("청크 응답 수신 (길이: {}자)", jsonText.length());

        GeminiProblemSet geminiResult = converter.convert(jsonText);

        if (geminiResult == null || geminiResult.quiz() == null
            || geminiResult.quiz().isEmpty()) {
            log.warn("청크 결과 비어있음: pages={}", chunk.referencedPages());
            return;
        }

        // ──── 선택지 수 검증 (문제 단위) ────
        GeminiProblem firstProblem = geminiResult.quiz().getFirst();
        if (firstProblem.selections() != null
            && firstProblem.selections().size() > MAX_SELECTION_COUNT) {
            log.warn("선택지 초과로 청크 폐기: {}개 선택지, pages={}",
                firstProblem.selections().size(), chunk.referencedPages());
            return;
        }

        AIProblemSet result = GeminiProblemSetMapper.toDto(
            geminiResult, strategyValue, numberCounter
        );

        onChunkCompleted.accept(result);

        log.debug("청크 처리 완료: pages={}, 문제 {}개",
            chunk.referencedPages(), result.quiz().size());
    } catch (Exception e) {
        log.error("청크 처리 실패 (계속 진행): pages={}, error={}",
            chunk.referencedPages(), e.getMessage());
        // ← 예외를 삼킴 → 다른 청크는 계속 진행
        // ← 전체 실패 판정은 generateQuiz()에서 수행
    }
}
```

### 전체 실패 판정 추가

```java
@Override
public void generateQuiz(
    String fileUrl,
    String strategyValue,
    int quizCount,
    List<Integer> referencePages,
    Consumer<AIProblemSet> onChunkCompleted
) {
    List<ChunkInfo> chunks = ChunkSplitter.createPageChunks(
        referencePages, quizCount, MAX_CHUNK_COUNT
    );
    log.info("청크 분할 완료: {}개 청크", chunks.size());

    chunkRateLimiter.acquire(chunks.size());

    FileMetadata metadata = geminiFileService.uploadPdf(fileUrl);
    log.info("업로드 완료: name={}, uri={}", metadata.name(), metadata.uri());

    var converter = new BeanOutputConverter<>(GeminiProblemSet.class);
    String jsonSchema = converter.getJsonSchema();

    String cacheName = geminiCacheService.createCache(
        metadata.uri(), strategyValue, jsonSchema
    );
    try {
        log.info("캐시 생성 완료: cacheName={}", cacheName);

        AtomicInteger numberCounter = new AtomicInteger(1);
        AtomicInteger failureCount = new AtomicInteger(0);  // ← NEW

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Semaphore concurrencyLimit = new Semaphore(MAX_CONCURRENT_CALLS);  // ← NEW

            List<CompletableFuture<Void>> futures = new ArrayList<>(chunks.size());

            for (ChunkInfo chunk : chunks) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    concurrencyLimit.acquireUninterruptibly();
                    try {
                        processChunk(
                            chunk, cacheName, converter, strategyValue,
                            numberCounter, onChunkCompleted
                        );
                    } catch (Exception e) {
                        failureCount.incrementAndGet();  // ← NEW
                        log.error("청크 처리 실패 (계속 진행): pages={}, error={}",
                            chunk.referencedPages(), e.getMessage());
                    } finally {
                        concurrencyLimit.release();
                    }
                }, executor);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }

        // ──── 전체 실패 판정 ──── (NEW)
        int totalChunks = chunks.size();
        int failed = failureCount.get();
        int succeeded = totalChunks - failed;

        log.info("병렬 생성 완료: 총 {}개 청크 중 {}개 성공, {}개 실패",
            totalChunks, succeeded, failed);

        if (succeeded == 0) {
            throw new RuntimeException(
                "전체 청크 실패: " + totalChunks + "개 청크 모두 실패"
            );
        }
    } finally {
        geminiCacheService.deleteCache(cacheName);
        geminiFileService.deleteFile(metadata.name());
    }
}
```

---

## 7단계: 에러 흐름 상세

### 청크 내부 에러 (부분 실패 허용)

```
processChunk() 내부:
  │
  ├── chatModel.call() 실패
  │     ├── 429 RESOURCE_EXHAUSTED → 로그 기록 + 해당 청크 skip
  │     ├── 400 INVALID_ARGUMENT  → 로그 기록 + 해당 청크 skip
  │     └── 500 INTERNAL          → 로그 기록 + 해당 청크 skip
  │
  ├── 응답 빈 텍스트 (null 또는 "")
  │     → IllegalStateException → 로그 기록 + 해당 청크 skip
  │
  ├── converter.convert() 실패
  │     → 로그 기록 + 해당 청크 skip
  │
  └── 선택지 4개 초과
        → 해당 청크만 폐기 (log.warn + return)
        → failureCount에 포함되지 않음 (예외가 아니라 조건 분기)

  ※ 모든 경우에 다른 청크는 계속 진행됨
```

### 전체 파이프라인 에러

```
generateQuiz() 호출 결과:
  │
  ├── 정상 (succeeded > 0) → 리턴 → AIServerAdapter로 복귀
  │
  ├── 전체 실패 (succeeded == 0)
  │     → RuntimeException throw
  │     → AIServerAdapter.fallback()에서 처리
  │     → AI_SERVER_COMMUNICATION_ERROR
  │
  ├── Rate Limit 초과 (acquire 실패)
  │     → CustomException(AI_SERVER_TO_MANY_REQUEST)
  │     → AIServerAdapter.fallback()에서 그대로 전파
  │
  ├── uploadPdf() 실패
  │     → CustomException(AI_SERVER_COMMUNICATION_ERROR)
  │     → AIServerAdapter.fallback()에서 처리
  │
  └── createCache() 실패
        → CustomException(AI_SERVER_RESPONSE_ERROR)
        → AIServerAdapter.fallback()에서 처리
```

### 이중 보호: Semaphore + Rate Limiter

```
보호 계층 1: ChunkRateLimiter — 분당 총 청크 수 제한
  → 시간 축에서의 보호 (1분에 75개 이하)
  → API 호출 전 사전 검증

보호 계층 2: Semaphore — 동시 호출 수 제한
  → 동시성 축에서의 보호 (동시에 5개 이하)
  → API 응답 대기 중 과부하 방지

시간 ─────────────────────────────────────────────>

[Rate Limiter] 1분에 75개 이하의 청크 허용
               ├ 요청 A (10 chunks) → 잔여 65
               ├ 요청 B (10 chunks) → 잔여 55
               └ 요청 H (10 chunks) → 거부 ❌

[Semaphore]    각 요청 내부에서 동시 5개씩 호출
               ├ VT-1~5 동시 호출
               └ VT-6~10 대기 → 완료된 슬롯에서 실행
```

---

## 완성 후 디렉토리 구조

```
modules/ai/
├── api/src/main/java/com/icc/qasker/ai/
│   ├── GeminiCacheService.java          (기존 — 인터페이스)
│   ├── GeminiFileService.java           (기존 — 인터페이스)
│   ├── QuizOrchestrationService.java    (기존 — 인터페이스)
│   └── dto/
│       ├── ChunkInfo.java               (기존 — 청크 정보 record)
│       ├── AIProblemSet.java            (기존 — 결과 DTO)
│       ├── AIProblem.java               (기존 — 문제 DTO)
│       ├── AISelection.java             (기존 — 선택지 DTO)
│       └── GeminiFileUploadResponse.java (기존 — Step 2)
│
└── impl/src/main/java/com/icc/qasker/ai/
    ├── config/
    │   ├── GeminiCacheConfig.java          (기존 — Step 3)
    │   └── GeminiFileRestClientConfig.java (기존 — Step 2)
    ├── mapper/
    │   └── GeminiProblemSetMapper.java     (기존 — Step 5)
    ├── prompt/
    │   └── quiz/ ...                       (기존 — Step 4)
    ├── service/
    │   ├── GeminiCacheServiceImpl.java     (기존 — Step 3+4)
    │   ├── GeminiFileServiceImpl.java      (기존 — Step 2)
    │   ├── QuizOrchestrationServiceImpl.java (수정 — Rate Limiter + Semaphore + 전체 실패 판정)
    │   └── ChunkRateLimiter.java          ← NEW (Bucket4j 기반 Rate Limiter)
    ├── structure/
    │   ├── GeminiProblemSet.java           (기존 — Gemini 응답 구조체)
    │   ├── GeminiProblem.java             (기존)
    │   └── GeminiSelection.java           (기존)
    └── util/
        ├── PdfUtils.java                  (기존 — Step 2)
        └── ChunkSplitter.java             (기존 — Step 5)
```

---

## 8단계: 설정 추가

### `application-local.yml`

```yaml
# 기존 설정 하단에 추가
gemini:
  rate-limiter:
    limit: 75            # 1분당 최대 청크 수
    window-seconds: 60   # 리필 주기 (초)
```

### `application-prod.yml`

```yaml
gemini:
  rate-limiter:
    limit: 75
    window-seconds: 60
```

> Gemini API Free tier RPM = 15, Pay-as-you-go RPM = 2000.
> `limit: 75`는 동시 사용자 수와 청크 크기를 고려한 안전 마진이다.
> 운영 환경에서 모니터링 후 조정한다.

---

## 9단계: 검증

### 9-1. 컴파일 확인

```bash
./gradlew :modules:ai:impl:compileJava
```

### 9-2. Rate Limiter 테스트

```
시나리오 1: 한도 내 요청
  → limit=75, 청크 10개 × 7회 = 70 소모
  → 모두 성공

시나리오 2: 한도 초과
  → limit=75, 청크 10개 × 8회 = 80 소모 시도
  → 8번째 요청에서 CustomException(AI_SERVER_TO_MANY_REQUEST) 발생
  → HTTP 429 응답: "서버가 생성요청 한도에 도달했습니다. 문제 개수를 줄이거나 1분 뒤 다시 시도해주세요."

시나리오 3: 리필 후 재시도
  → 한도 초과 후 60초 대기
  → 다시 요청 → 성공
```

### 9-3. Circuit Breaker 테스트

```
시나리오 1: 연속 실패 → OPEN
  → 잘못된 API 키로 연속 3회 호출
  → 3번째 이후 Circuit OPEN
  → 4번째 호출 → CallNotPermittedException → 즉시 거부

시나리오 2: OPEN → HALF_OPEN → CLOSED
  → Circuit OPEN 상태에서 10초 대기
  → 자동으로 HALF_OPEN 전이
  → 2회 호출 허용 → 성공 시 CLOSED로 복구
```

### 9-4. 부분 실패 테스트

```
시나리오: 10개 청크 중 1개 실패
  → 9개 청크의 결과가 정상적으로 SSE 스트리밍됨
  → 로그: "병렬 생성 완료: 총 10개 청크 중 9개 성공, 1개 실패"
  → GenerationServiceImpl에서 finalizePartialSuccess() 호출
```

### 기대 로그

```
INFO  QuizOrchestrationServiceImpl - 청크 분할 완료: 10개 청크
DEBUG ChunkRateLimiter              - Rate Limit 통과: 소모=10, 잔여=65
INFO  QuizOrchestrationServiceImpl - 업로드 완료: name=files/r1b5ugz, uri=https://...
INFO  QuizOrchestrationServiceImpl - 캐시 생성 완료: cacheName=cachedContents/abc123
DEBUG QuizOrchestrationServiceImpl - 청크 처리 시작: pages=[1,2,3], quizCount=2
  ... (최대 5개 동시)
DEBUG QuizOrchestrationServiceImpl - 청크 처리 완료: pages=[4,5,6], 문제 2개
  → SSE 이벤트 즉시 전송됨
INFO  QuizOrchestrationServiceImpl - 병렬 생성 완료: 총 10개 청크 중 10개 성공, 0개 실패
INFO  GeminiCacheServiceImpl       - 캐시 삭제 완료: name=cachedContents/abc123
INFO  GeminiFileServiceImpl        - 파일 삭제 완료: name=files/r1b5ugz
```

### 검증 체크리스트

| 항목 | 확인 포인트 |
|---|---|
| **Rate Limiter 초기화** | 앱 기동 시 "ChunkRateLimiter 초기화: limit=75, windowSeconds=60" 로그 |
| **Rate Limit 통과** | 요청 시 "Rate Limit 통과: 소모=10, 잔여=65" 로그 |
| **Rate Limit 거부** | 한도 초과 시 429 응답 + "Rate Limit 초과" 로그 |
| **Rate Limit 위치** | PDF 업로드 전에 거부되는가? (uploadPdf 로그 없이 바로 429) |
| **Circuit Breaker** | 연속 실패 시 OPEN 전이 확인 |
| **ignore-exceptions** | ClientSideException은 Circuit 실패 카운트에 불포함 |
| **부분 실패** | 일부 청크 실패 시 나머지 결과 정상 스트리밍 |
| **전체 실패** | 모든 청크 실패 시 RuntimeException 발생 |
| **Semaphore** | 동시 "청크 처리 시작" 로그가 5개 이하 |
| **리소스 정리** | 에러 발생 시에도 캐시 삭제 + 파일 삭제 로그 확인 |

---

## 이후 Step 프리뷰

### Step 8: quiz-impl 연결 + 정리

```java
// AIServerAdapter에서 QuizOrchestrationService를 직접 호출하는 구조로 전환
// 기존 RestClient 기반 AI 서버 통신 코드 제거
// AIProblemSet → ProblemSetGeneratedEvent 매퍼 작성

// 제거 대상:
// - RestClientConfig.java (AI 서버용)
// - FeRequestToAIRequestMapper.java
// - GenerationRequestToAI.java
// - StreamEvent.java, ErrorEvent.java
```

---

## 참고 링크

- [Bucket4j GitHub](https://github.com/bucket4j/bucket4j) -- 토큰 버킷 알고리즘 개요
- [Bucket4j Reference — Basic Usage](https://bucket4j.com/8.14.0/toc.html) -- Bucket.builder(), tryConsume(N), Bandwidth 설정
- [Maven Central — bucket4j_jdk17-core](https://central.sonatype.com/artifact/com.bucket4j/bucket4j_jdk17-core) -- 의존성 좌표/최신 버전
- [Resilience4j — CircuitBreaker](https://resilience4j.readme.io/docs/circuitbreaker) -- 상태 전이, 설정 파라미터
- [Resilience4j — Spring Boot 3 통합](https://resilience4j.readme.io/docs/getting-started-3) -- 어노테이션 기반 설정
- [Spring AI — Error Handling](https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html) -- Spring AI 예외 타입