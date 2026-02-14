# Step 6: 청크 분할 + 병렬 배치 생성 (Virtual Threads)

> Java 21 Virtual Threads로 청크별 Gemini API를 병렬 호출하고,
> 청크 완료 즉시 `Consumer` 콜백을 호출하여 SSE 스트리밍에 결과를 전달한다.

---

## 전체 플로우

```
Client ← SSE (text/event-stream)
  ↑
  │ emitter.send() ← 청크 완료 즉시
  │
GenerationServiceImpl (Virtual Thread)
  │
  │ Consumer<ProblemSetGeneratedEvent>
  │   └ doMainLogic() → DB 저장 → emitter.send()
  │
AIServerAdapter.streamRequest()
  │
  │ Consumer<List<GeneratedProblem>>
  │   └ List<GeneratedProblem> → ProblemSetGeneratedEvent 매핑
  │
QuizOrchestrationServiceImpl.generateQuizzes()
  │
  ├── 1. GeminiFileService.uploadPdf(fileUrl)                [기존 — Step 2]
  ├── 2. BeanOutputConverter<AIProblemSet>.getJsonSchema()    [기존 — Step 4]
  ├── 3. GeminiCacheService.createCache(uri, strategy, schema)[기존 — Step 3+4]
  │
  ├── 4. ChunkSplitter.createPageChunks(pages, count, MAX)   ← NEW
  │
  ├── 5. [Virtual Thread Pool + Semaphore] 각 청크 병렬 실행:  ← NEW
  │     ├ UserPrompt.generate(chunk.referencedPages, chunk.quizCount)
  │     ├ chatModel.call(prompt, options { cacheName })
  │     ├ BeanOutputConverter.convert(jsonText) → AIProblemSet
  │     ├ 검증: 선택지 4개 초과 문제 폐기
  │     ├ 선택지 셔플 (MULTIPLE/BLANK)
  │     ├ 번호 재할당 (AtomicInteger)
  │     └ Consumer 콜백 호출 ← 즉시 SSE 전송됨
  │
  ├── 6. 에러 수집 + 부분 실패 판정
  │
  └── 7. finally: cacheService.deleteCache + fileService.deleteFile
```

---

## 핵심 설계 원칙

### 청크 완료 즉시 스트리밍

```
quiz 모듈의 GenerationServiceImpl은 SSE(Server-Sent Events)로 결과를 스트리밍한다.
청크가 완료될 때마다 Consumer 콜백이 호출되고, 다음 체인이 즉시 실행된다:

  Consumer<List<GeneratedProblem>> 호출
    → AIServerAdapter: GeneratedProblem → ProblemSetGeneratedEvent 매핑
      → GenerationServiceImpl.doMainLogic(): DB 저장 → emitter.send()
        → 클라이언트에 SSE 이벤트 전달

모든 청크가 완료될 때까지 기다리지 않는다.
청크 1이 완료되면 즉시 클라이언트에 전달되고,
청크 2~N은 백그라운드에서 계속 처리된다.
```

### 동시 호출 제한

```
가상 스레드가 저렴하더라도, Gemini API에는 RPM(분당 요청 수) 제한이 있다.
10개 청크를 동시에 날리면 429 RESOURCE_EXHAUSTED 에러가 발생할 수 있다.

→ Semaphore로 동시 호출 수를 제한한다. (기본값: 5)
→ Step 7에서 Bucket4j Rate Limiter와 결합하여 이중 보호.
```

---

## 병렬 호출 + 즉시 스트리밍 구조

```
                   ┌─ 캐시 (PDF + 시스템 프롬프트 + JSON Schema) ─┐
                   │              1개 공유 (read-only)             │
                   └──────────────┬───────────────────────────────┘
                                  │
                            ┌─────┴─────┐
                            │ Semaphore │  ← 동시 호출 수 제한 (기본 5)
                            └─────┬─────┘
                                  │
          ┌───────────┬───────────┼───────────┬───────────┐
          │           │           │           │           │
     [VT-1]      [VT-2]     [VT-3]      [VT-4]     [VT-N]
    chunk-1      chunk-2    chunk-3     chunk-4     chunk-N
   pages[1-3]   pages[4-6] pages[7-9]  pages[10-12]  ...
   quizCount=2  quizCount=2 quizCount=1 quizCount=1  ...
          │           │           │           │           │
          ▼           ▼           ▼           ▼           ▼
    chatModel     chatModel   chatModel  chatModel   chatModel
     .call()       .call()     .call()    .call()     .call()
          │           │           │           │           │
          ▼           ▼           ▼           ▼           ▼
    검증+셔플     검증+셔플   (예외발생)  검증+셔플    검증+셔플
    번호할당      번호할당    (로그기록)   번호할당     번호할당
          │           │                     │           │
          ▼           ▼                     ▼           ▼
    Consumer     Consumer              Consumer    Consumer
    콜백 호출      콜백 호출               콜백 호출     콜백 호출
       │             │                     │           │
       ▼             ▼                     ▼           ▼
    SSE 전송      SSE 전송              SSE 전송     SSE 전송
   (즉시!)       (즉시!)               (즉시!)      (즉시!)

→ 완료 순서 비결정적 (순서 보장 X)
→ 번호는 AtomicInteger로 스레드 안전하게 순차 재할당
→ 실패한 청크는 로그 기록 후 건너뜀 (부분 실패 허용)
→ 전체 실패(성공 0개)인 경우 예외 전파
```

---

## 완성 후 디렉토리 구조

```
modules/ai/
├── api/src/main/java/com/icc/qasker/ai/
│   ├── GeminiCacheService.java          (기존 — 인터페이스)
│   ├── GeminiFileService.java           (기존 — 인터페이스)
│   ├── QuizOrchestrationService.java    (수정 — Consumer 콜백 + 청크 파라미터)
│   └── dto/
│       ├── ChunkInfo.java               (기존 — 청크 정보 record)
│       ├── GeneratedProblem.java        ← NEW (결과 DTO)
│       ├── GeneratedSelection.java      ← NEW (선택지 DTO)
│       └── GeminiFileUploadResponse.java (기존 — Step 2)
│
└── impl/src/main/java/com/icc/qasker/ai/
    ├── config/
    │   ├── GeminiCacheConfig.java          (기존 — Step 3)
    │   └── GeminiFileRestClientConfig.java (기존 — Step 2)
    ├── prompt/
    │   └── quiz/ ...                       (기존 — Step 4)
    ├── service/
    │   ├── GeminiCacheServiceImpl.java     (기존 — Step 3+4)
    │   ├── GeminiFileServiceImpl.java      (기존 — Step 2)
    │   └── QuizOrchestrationServiceImpl.java (대폭 수정 — 병렬 파이프라인)
    ├── structure/
    │   ├── AIProblemSet.java              (기존 — Step 4, impl 내부 전용)
    │   ├── AIProblem.java                 (기존 — Step 4, impl 내부 전용)
    │   └── AISelection.java              (기존 — Step 4, impl 내부 전용)
    └── util/
        ├── PdfUtils.java                  (기존 — Step 2)
        └── ChunkSplitter.java            ← NEW (청크 분할 유틸리티)
```

---

## 배경: 왜 청크를 나누고 병렬로 호출하는가?

> Step 5 참조. Step 6에서 추가된 이점:

5. **점진적 전달**: 완료된 청크부터 즉시 SSE로 스트리밍 → 사용자 대기 시간 최소화

---

## 1단계: api 모듈 DTO 정의

> ai-impl의 `AIProblemSet/AIProblem/AISelection`은 Spring AI의 `BeanOutputConverter`용 내부 구조체다.
> 외부(quiz-impl)에 노출하는 결과는 api 모듈의 DTO로 변환하여 모듈 경계를 깔끔하게 유지한다.

### `GeneratedSelection.java`

**경로**: `modules/ai/api/src/main/java/com/icc/qasker/ai/dto/GeneratedSelection.java`

```java
package com.icc.qasker.ai.dto;

public record GeneratedSelection(
    String content,
    boolean correct
) {

}
```

### `GeneratedProblem.java`

**경로**: `modules/ai/api/src/main/java/com/icc/qasker/ai/dto/GeneratedProblem.java`

```java
package com.icc.qasker.ai.dto;

import java.util.List;

public record GeneratedProblem(
    int number,
    String title,
    List<GeneratedSelection> selections,
    String explanation,
    List<Integer> referencedPages
) {

}
```

### 왜 `referencedPages`를 포함하는가?

```
AIProblem (impl 내부):
  number, title, selections, explanation
  → LLM이 생성한 원본. 어떤 페이지를 참고했는지 모름.

GeneratedProblem (api 외부):
  number, title, selections, explanation, referencedPages
  → 청크의 referencedPages를 문제에 부착.
  → quiz-impl의 ProblemSetGeneratedEvent.QuizGeneratedFromAI에 대응.
  → 사용자에게 "이 문제는 3~5페이지를 참고하여 생성됨"을 전달 가능.
```

---

## 2단계: `ChunkInfo.java` / `ChunkSplitter.java`

> Step 5에서 구현 완료. 코드와 알고리즘은 Step 5 참조.
> 경로만 모듈 분리에 따라 변경됨:
> - `ChunkInfo.java`: `modules/ai/api/.../dto/ChunkInfo.java`
> - `ChunkSplitter.java`: `modules/ai/impl/.../util/ChunkSplitter.java`

---

## 3단계: `QuizOrchestrationService` 인터페이스 수정

**경로**: `modules/ai/api/src/main/java/com/icc/qasker/ai/QuizOrchestrationService.java`

```java
package com.icc.qasker.ai;

import com.icc.qasker.ai.dto.ChunkInfo;
import com.icc.qasker.ai.dto.GeneratedProblem;
import java.util.List;
import java.util.function.Consumer;

public interface QuizOrchestrationService {

    /**
     * PDF에서 퀴즈를 병렬 생성하고, 청크 완료 시마다 Consumer 콜백을 호출한다.
     *
     * <p>내부적으로 청크별 Gemini API 호출을 병렬 수행하며,
     * 각 청크가 완료되면 검증/셔플/번호 할당 후 즉시 콜백을 호출한다.
     * 모든 청크가 완료될 때까지 블로킹된다.
     *
     * <p>부분 실패를 허용한다: 10개 청크 중 1개가 실패하면 9개의 결과만 콜백된다.
     * 전체 청크가 실패한 경우에만 예외를 던진다.
     *
     * @param fileUrl          PDF 파일 URL
     * @param strategy         퀴즈 타입 문자열 ("MULTIPLE", "BLANK", "OX")
     * @param chunks           청크 목록 (각 청크: 참조 페이지 + 문제 수)
     * @param onChunkCompleted 청크 완료 시 호출되는 콜백
     * @throws RuntimeException 전체 청크가 실패한 경우
     */
    void generateQuizzes(
        String fileUrl,
        String strategy,
        List<ChunkInfo> chunks,
        Consumer<List<GeneratedProblem>> onChunkCompleted
    );
}
```

> 청크 분할(`ChunkSplitter`)을 호출자(quiz-impl)에서 수행하는 이유:
> - quiz-impl이 `quizCount`, `pageNumbers`, `maxChunkCount` 정책을 소유한다
> - ai 모듈은 "주어진 청크를 병렬로 처리"하는 역할만 담당한다

---

## 4단계: `QuizOrchestrationServiceImpl` — 병렬 파이프라인 구현

**경로**: `modules/ai/impl/src/main/java/com/icc/qasker/ai/service/QuizOrchestrationServiceImpl.java`

```java
package com.icc.qasker.ai.service;

import com.icc.qasker.ai.GeminiCacheService;
import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.ChunkInfo;
import com.icc.qasker.ai.dto.GeneratedProblem;
import com.icc.qasker.ai.dto.GeneratedSelection;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.prompt.quiz.common.QuizPromptStrategy;
import com.icc.qasker.ai.prompt.quiz.common.QuizType;
import com.icc.qasker.ai.prompt.quiz.user.UserPrompt;
import com.icc.qasker.ai.structure.AIProblem;
import com.icc.qasker.ai.structure.AIProblemSet;
import com.icc.qasker.ai.structure.AISelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizOrchestrationServiceImpl implements QuizOrchestrationService {

    private static final int MAX_CONCURRENT_CALLS = 5;
    private static final int MAX_SELECTION_COUNT = 4;

    private final GeminiFileService geminiFileService;
    private final GeminiCacheService geminiCacheService;
    private final ChatModel chatModel;

    @Override
    public void generateQuizzes(
        String fileUrl,
        String strategyValue,
        List<ChunkInfo> chunks,
        Consumer<List<GeneratedProblem>> onChunkCompleted
    ) {
        // ──── 준비: PDF 업로드 + 캐시 생성 ────
        FileMetadata metadata = geminiFileService.uploadPdf(fileUrl);
        log.info("업로드 완료: name={}, uri={}", metadata.name(), metadata.uri());

        var converter = new BeanOutputConverter<>(AIProblemSet.class);
        String jsonSchema = converter.getJsonSchema();

        QuizPromptStrategy strategy = QuizType.valueOf(strategyValue);
        String cacheName = null;

        try {
            cacheName = geminiCacheService.createCache(
                metadata.uri(), strategyValue, jsonSchema
            );
            log.info("캐시 생성 완료: cacheName={}", cacheName);

            // ──── 병렬 실행: Virtual Threads + Semaphore ────
            executeParallel(
                chunks, cacheName, converter, strategy, onChunkCompleted
            );

        } finally {
            // ──── 리소스 정리 ────
            if (cacheName != null) {
                geminiCacheService.deleteCache(cacheName);
            }
            geminiFileService.deleteFile(metadata.name());
        }
    }

    /**
     * 각 청크를 Virtual Thread에서 병렬 호출하고,
     * 완료된 청크는 즉시 Consumer 콜백으로 전달한다.
     */
    private void executeParallel(
        List<ChunkInfo> chunks,
        String cacheName,
        BeanOutputConverter<AIProblemSet> converter,
        QuizPromptStrategy strategy,
        Consumer<List<GeneratedProblem>> onChunkCompleted
    ) {
        log.info("청크 {}개 병렬 처리 시작 (동시 호출 제한: {})",
            chunks.size(), MAX_CONCURRENT_CALLS);

        AtomicInteger numberCounter = new AtomicInteger(1);
        AtomicInteger failureCount = new AtomicInteger(0);
        Semaphore concurrencyLimit = new Semaphore(MAX_CONCURRENT_CALLS);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = chunks.stream()
                .map(chunk -> CompletableFuture.runAsync(() -> {
                    concurrencyLimit.acquireUninterruptibly();
                    try {
                        processChunk(
                            chunk, cacheName, converter, strategy,
                            numberCounter, onChunkCompleted
                        );
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        log.error("청크 처리 실패 (계속 진행): pages={}, error={}",
                            chunk.referencedPages(), e.getMessage());
                    } finally {
                        concurrencyLimit.release();
                    }
                }, executor))
                .toList();

            // 모든 청크 완료 대기
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }
        // ← executor.close() → 모든 Virtual Thread 종료 보장

        // 결과 집계
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
    }

    /**
     * 단일 청크를 처리한다: API 호출 → 검증 → 셔플 → 번호 할당 → 콜백.
     *
     * <p>이 메서드는 Virtual Thread에서 실행된다.
     */
    private void processChunk(
        ChunkInfo chunk,
        String cacheName,
        BeanOutputConverter<AIProblemSet> converter,
        QuizPromptStrategy strategy,
        AtomicInteger numberCounter,
        Consumer<List<GeneratedProblem>> onChunkCompleted
    ) {
        log.debug("청크 API 호출: pages={}, quizCount={}",
            chunk.referencedPages(), chunk.quizCount());

        // ──── 1. 유저 프롬프트 조립 + API 호출 ────
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

        if (jsonText == null || jsonText.isBlank()) {
            throw new IllegalStateException(
                "Gemini 응답이 비어있음: pages=" + chunk.referencedPages()
            );
        }

        log.debug("청크 응답 수신 (길이: {}자)", jsonText.length());

        // ──── 2. JSON → AIProblemSet 역직렬화 ────
        AIProblemSet problemSet = converter.convert(jsonText);

        if (problemSet == null || problemSet.quiz() == null || problemSet.quiz().isEmpty()) {
            throw new IllegalStateException(
                "파싱 결과가 비어있음: pages=" + chunk.referencedPages()
            );
        }

        // ──── 3. 후처리: 검증 + 셔플 + 번호 할당 + DTO 변환 ────
        boolean shouldShuffle = strategy == QuizType.MULTIPLE || strategy == QuizType.BLANK;
        List<GeneratedProblem> problems = new ArrayList<>();

        for (AIProblem problem : problemSet.quiz()) {
            if (problem.selections() != null
                && problem.selections().size() > MAX_SELECTION_COUNT) {
                log.warn("선택지 초과로 문제 폐기: {}개 선택지, pages={}",
                    problem.selections().size(), chunk.referencedPages());
                continue;
            }

            List<GeneratedSelection> selections = mapSelections(
                problem.selections(), shouldShuffle
            );

            problems.add(new GeneratedProblem(
                numberCounter.getAndIncrement(),
                problem.title(),
                selections,
                problem.explanation(),
                chunk.referencedPages()
            ));
        }

        // ──── 4. 콜백 호출 → SSE 스트리밍으로 전달됨 ────
        onChunkCompleted.accept(problems);

        log.debug("청크 처리 완료: pages={}, 문제 {}개",
            chunk.referencedPages(), problems.size());
    }

    private List<GeneratedSelection> mapSelections(
        List<AISelection> selections, boolean shouldShuffle
    ) {
        if (selections == null || selections.isEmpty()) {
            return List.of();
        }

        List<AISelection> ordered = shouldShuffle
            ? shuffled(selections)
            : selections;

        return ordered.stream()
            .map(s -> new GeneratedSelection(s.content(), s.correct()))
            .toList();
    }

    private static <T> List<T> shuffled(List<T> original) {
        List<T> copy = new ArrayList<>(original);
        Collections.shuffle(copy);
        return copy;
    }
}
```

### 설계 결정

| 결정 | 근거 |
|---|---|
| **`Consumer` 콜백** | 청크 완료 즉시 SSE 스트리밍에 전달. 모든 청크 완료를 기다리지 않음 |
| **청크 단위 후처리** | 검증/셔플/번호 할당을 각 Virtual Thread 안에서 처리. 후처리 완료 후 즉시 콜백 |
| **`AtomicInteger`** | 여러 Virtual Thread에서 동시 접근 → CAS 기반 thread-safe increment |
| **`getQuietly` 제거** | 예외를 CompletableFuture에 위임하지 않고 try-catch에서 직접 처리 + failureCount 집계 |
| **문제 단위 검증** | 모든 문제의 선택지 수를 개별 검증하여 초과 문제만 폐기 |
| **`Semaphore(5)`** | Gemini API RPM 제한 보호. `MAX_CONCURRENT_CALLS`로 외부 설정 확장 여지 확보 |
| **`shuffled()` 헬퍼** | record 불변성 유지. 원본 리스트를 변경하지 않고 새 리스트 반환 |

### Virtual Threads + Semaphore 상호작용

```
Semaphore permits = 5
청크 10개를 제출한 경우:

시간 ─────────────────────────────────────────────>

VT-1:  [====== API 호출 ======]                        [완료 → 콜백 → SSE]
VT-2:  [====== API 호출 ======]                        [완료 → 콜백 → SSE]
VT-3:  [====== API 호출 ======]                        [완료 → 콜백 → SSE]
VT-4:  [====== API 호출 ======]                        [완료 → 콜백 → SSE]
VT-5:  [====== API 호출 ======]                        [완료 → 콜백 → SSE]
VT-6:  (대기...)              [====== API 호출 ======]  [완료 → 콜백 → SSE]
VT-7:  (대기...)              [====== API 호출 ======]  [완료 → 콜백 → SSE]
VT-8:  (대기...)              [====== API 호출 ======]  [완료 → 콜백 → SSE]
VT-9:  (대기...)              [====== API 호출 ======]  [완료 → 콜백 → SSE]
VT-10: (대기...)              [====== API 호출 ======]  [완료 → 콜백 → SSE]

→ 가상 스레드 10개가 즉시 생성되지만, Semaphore가 동시 API 호출을 5개로 제한
→ 완료된 청크는 대기 없이 즉시 콜백 → SSE 전송
→ API 호출 중 블로킹되어도 OS 스레드는 해제됨 (Virtual Thread의 핵심)
```

### ChatModel Thread-Safety

> Step 5 참조. ChatModel은 stateless — N개 Virtual Thread 동시 호출 안전.

### Consumer 콜백 Thread-Safety

```
Consumer 콜백은 여러 Virtual Thread에서 동시에 호출된다.

quiz 모듈의 콜백 체인:
  Consumer<List<GeneratedProblem>>
    → AIServerAdapter: GeneratedProblem → ProblemSetGeneratedEvent 매핑
      → GenerationServiceImpl.doMainLogic()
        → problemRepository.saveAll()  ← JPA Repository는 thread-safe
        → emitter.send()              ← SseEmitter.send()는 synchronized

따라서 quiz 모듈의 기존 콜백 체인은 thread-safe하다.
```

---

## 5단계: 에러 처리 흐름

### 청크 단위 에러 (Virtual Thread 내부)

```
processChunk() 내부:
  │
  ├── chatModel.call() 실패
  │     ├── 429 RESOURCE_EXHAUSTED → TransientAiException
  │     ├── 400 INVALID_ARGUMENT  → NonTransientAiException
  │     └── 500 INTERNAL          → TransientAiException
  │
  ├── 응답 빈 텍스트 (null 또는 "")
  │     → IllegalStateException
  │
  ├── converter.convert() 실패
  │     → OutputConversionException
  │
  └── 선택지 4개 초과
        → 해당 문제만 폐기 (log.warn + continue)
        → 나머지 문제는 정상 처리 후 콜백 호출

  processChunk 예외 → catch에서 failureCount++ 후 로그 기록
                    → 해당 청크의 콜백은 호출되지 않음
                    → 나머지 청크는 계속 진행
```

### 전체 실패 판정

```
모든 청크 완료 후:
  │
  ├── succeeded > 0  → 정상 종료 (일부 실패 허용)
  │
  └── succeeded == 0 → RuntimeException throw
        → "전체 청크 실패" → 호출자에게 전파
        → GenerationServiceImpl.finalizeError()에서 처리
```

### finally 블록의 안전성

```
try (ExecutorService executor = ...) {
    // CompletableFuture 제출 + allOf().join()
}
// ← executor.close() 호출됨
//   → shutdown() + awaitTermination()
//   → 모든 Virtual Thread가 종료될 때까지 대기
//   → 이 시점 이후로는 어떤 스레드도 캐시를 참조하지 않음

finally {
    geminiCacheService.deleteCache(cacheName);  // 안전
    geminiFileService.deleteFile(metadata.name());  // 안전
}
```

---

## 6단계: 호출자 코드 예시 (quiz-impl에서의 사용)

> Step 7에서 실제로 연결할 때의 코드 구조를 미리 보여준다.

```java
// AIServerAdapter.java (Step 7에서 구현)

@CircuitBreaker(name = "aiServer", fallbackMethod = "fallback")
public void streamRequest(GenerationRequestToAI request,
    Consumer<ProblemSetGeneratedEvent> onLineReceived) {

    List<ChunkInfo> chunks = ChunkSplitter.createPageChunks(
        request.pageNumbers(), request.quizCount(), MAX_CHUNK_COUNT
    );

    orchestrationService.generateQuizzes(
        request.uploadedUrl(),
        request.quizType().name(),
        chunks,
        (problems) -> {
            ProblemSetGeneratedEvent event = mapToEvent(problems);
            onLineReceived.accept(event);
        }
    );
}
```

---

## 7단계: 검증

### 7-1. 컴파일 확인

```bash
./gradlew :modules:ai:impl:compileJava
```

### 7-2. 테스트 호출

> Step 5의 curl 명령어와 동일 (엔드포인트, 파라미터 형식 참조).

### 기대 로그

```
INFO  QuizOrchestrationServiceImpl - 업로드 완료: name=files/r1b5ugz, uri=https://...
INFO  QuizOrchestrationServiceImpl - 캐시 생성 완료: cacheName=cachedContents/abc123
INFO  QuizOrchestrationServiceImpl - 청크 10개 병렬 처리 시작 (동시 호출 제한: 5)
DEBUG QuizOrchestrationServiceImpl - 청크 API 호출: pages=[1,2,3], quizCount=2
DEBUG QuizOrchestrationServiceImpl - 청크 API 호출: pages=[4,5,6], quizCount=2
  ... (최대 5개 동시)
DEBUG QuizOrchestrationServiceImpl - 청크 응답 수신 (길이: 856자)
DEBUG QuizOrchestrationServiceImpl - 청크 처리 완료: pages=[4,5,6], 문제 2개
  → SSE 이벤트 즉시 전송됨
DEBUG QuizOrchestrationServiceImpl - 청크 처리 완료: pages=[1,2,3], 문제 2개
  → SSE 이벤트 즉시 전송됨
  ... (완료 순서는 비결정적)
INFO  QuizOrchestrationServiceImpl - 병렬 생성 완료: 총 10개 청크 중 10개 성공, 0개 실패
INFO  GeminiCacheServiceImpl       - 캐시 삭제 완료: name=cachedContents/abc123
INFO  GeminiFileServiceImpl        - 파일 삭제 완료: name=files/r1b5ugz
```

### 검증 체크리스트

| 항목 | 확인 포인트 |
|---|---|
| **청크 수** | quizCount=15, maxChunkCount=10 → 10개 청크 생성 |
| **SSE 스트리밍** | 첫 번째 청크 완료 시 클라이언트에 즉시 전달되는가? |
| **문제 번호** | 전체에서 고유한가? (AtomicInteger로 중복 없이) |
| **동시성 제한** | 로그에서 동시에 "청크 API 호출" 로그가 5개 이하인가? |
| **선택지 수** | 각 문제마다 4개 이하인가? (초과 문제는 개별 폐기) |
| **선택지 순서** | MULTIPLE/BLANK → 셔플되었는가? (같은 호출 반복 시 순서 변화) |
| **OX 셔플 안 됨** | OX 타입 → 선택지 순서 유지 (O, X) |
| **referencedPages** | 각 문제에 청크의 참조 페이지가 부착되어 있는가? |
| **부분 실패** | 의도적으로 한 청크 실패시켜도 나머지 결과 정상 스트리밍 |
| **전체 실패** | 모든 청크 실패 시 예외가 발생하는가? |
| **병렬 효과** | Step 4의 단일 호출 대비 소요 시간 비교 (총 시간 ≈ 단일 청크 시간) |
| **리소스 정리** | 캐시 삭제 로그 + 파일 삭제 로그 확인 |

---

## Gemini HTTP 메시지 (병렬 호출)

> Step 5 참조. 캐시 생성(1회) + 청크별 generateContent(N회 병렬) 구조 동일.
> Step 6에서 달라진 점: Semaphore로 **동시 요청을 5개로 제한**하여 429 에러를 예방한다.

---

## 이후 Step 프리뷰

### Step 7: Rate Limiter + Circuit Breaker

```java
// AIServerAdapter에 추가될 내용:

// Rate Limiter — 청크 수만큼 토큰 소모 (API 호출 전 사전 검증)
rateLimiter.acquire(chunks.size());

// Circuit Breaker — Gemini API 연속 장애 시 빠른 실패
@CircuitBreaker(name = "aiServer", fallbackMethod = "fallback")
```

### Step 8: quiz-impl 연결

```java
// AIServerAdapter에서 QuizOrchestrationService 호출:
List<ChunkInfo> chunks = ChunkSplitter.createPageChunks(
    request.pageNumbers(), request.quizCount(), MAX_CHUNK_COUNT
);

orchestrationService.generateQuizzes(
    request.uploadedUrl(),
    request.quizType().name(),
    chunks,
    (problems) -> {
        ProblemSetGeneratedEvent event = mapToEvent(problems);
        onLineReceived.accept(event);
    }
);
```

---

## 참고 링크

- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444) — Java 21 Virtual Threads 공식 스펙
- [Executors.newVirtualThreadPerTaskExecutor() Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html#newVirtualThreadPerTaskExecutor())
- [Semaphore Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Semaphore.html)
- [CompletableFuture Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Spring AI — Structured Output Converter](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
- [Gemini API — Context Caching](https://ai.google.dev/gemini-api/docs/caching)
- [Gemini API — 모델 제한](https://ai.google.dev/gemini-api/docs/models/gemini) — 모델별 RPM/TPM 제한
