# Step 5: 청크 분할 + 병렬 배치 생성

> Java 21 Virtual Threads로 청크별 Gemini API를 병렬 호출하고, `Consumer<AIProblemSet>` 콜백으로 결과를 스트리밍한다.

---

## 전체 플로우

```
fileUrl (PDF 경로), quizType, quizCount, pageNumbers
       │
       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Step 5에서 구현하는 영역                                                   │
│                                                                          │
│  1. GeminiFileService.uploadPdf(fileUrl)                 [기존 — Step 2]  │
│     └ PDF 업로드 + ACTIVE 상태까지 폴링                                     │
│                                                                          │
│  2. BeanOutputConverter<AIProblemSet>.getFormat()         [기존 — Step 4]  │
│     └ JSON Schema 자동 생성                                               │
│                                                                          │
│  3. GeminiCacheService.createCache(uri, strategy, schema) [기존 — Step 3+4]│
│     └ PDF + 시스템 프롬프트 + JSON Schema를 캐시에 포함                       │
│                                                                          │
│  4. ChunkSplitter.createPageChunks(pageNumbers, quizCount, MAX)  ← NEW   │
│     └ Python create_chunks.py 포팅                                        │
│     └ List<ChunkInfo> 반환                                                │
│                                                                          │
│  5. [Virtual Thread Pool] 각 청크 병렬 실행:                         ← NEW  │
│     ├ UserPrompt.generate(chunk.referencedPages, chunk.quizCount)         │
│     ├ chatModel.call(prompt, options { cacheName, "application/json" })   │
│     ├ BeanOutputConverter.convert(jsonText) → AIProblemSet                │
│     ├ 검증: 선택지 4개 초과 → 해당 배치 폐기                                  │
│     ├ 선택지 셔플 (MULTIPLE/BLANK)                                         │
│     ├ 번호 재할당 (1부터 순차 증가)                                           │
│     └ Consumer<AIProblemSet> 콜백 호출                                     │
│                                                                          │
│  6. finally: cacheService.deleteCache + fileService.deleteFile    [정리]   │
└──────────────────────────────────────────────────────────────────────────┘
       │
       ▼
Consumer<AIProblemSet>에 청크 완료 순서대로 콜백
→ Step 7에서 quiz-impl의 GenerationServiceImpl과 연결
```

### 병렬 호출 구조

```
                   ┌─ 캐시 (PDF + 시스템 프롬프트 + JSON Schema) ─┐
                   │              1개 공유                        │
                   └──────────────┬───────────────────────────────┘
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
  AIProblemSet  AIProblemSet    null   AIProblemSet  AIProblemSet
   (2문제)       (2문제)      (폐기)     (1문제)      ...
          │           │                     │           │
          ▼           ▼                     ▼           ▼
    Consumer     Consumer              Consumer    Consumer
    콜백 호출      콜백 호출               콜백 호출     콜백 호출

→ 완료 순서대로 콜백 (순서 보장 X)
→ 번호는 콜백 전 순차 재할당
```

---

## 완성 후 디렉토리 구조

```
modules/ai/src/main/java/com/icc/qasker/ai/
├── config/
│   ├── GeminiCacheConfig.java          (기존 — Step 3)
│   └── GeminiFileRestClientConfig.java (기존 — Step 2)
├── controller/
│   └── AIController.java               (수정 — 병렬 생성 테스트 엔드포인트 추가)
├── dto/
│   ├── ai/
│   │   ├── AIProblemSet.java          (기존 — Step 4)
│   │   ├── AIProblem.java             (기존 — Step 4)
│   │   └── AISelection.java          (기존 — Step 4)
│   ├── ChunkInfo.java                 ← NEW (청크 정보 record)
│   ├── ChatRequest.java               (기존)
│   ├── GeminiFileUploadResponse.java   (기존 — Step 2)
│   └── MyChatResponse.java            (기존)
├── prompt/
│   └── quiz/
│       ├── common/
│       │   ├── QuizPromptStrategy.java (기존 — Step 4)
│       │   └── QuizType.java          (기존 — Step 4)
│       ├── system/
│       │   └── SystemPrompt.java      (기존 — Step 4)
│       ├── user/
│       │   └── UserPrompt.java        (기존 — Step 4)
│       ├── blank/
│       │   ├── BlankFormat.java       (기존 — Step 4)
│       │   └── BlankGuideLine.java    (기존 — Step 4)
│       ├── mutiple/
│       │   ├── MultipleFormat.java    (기존 — Step 4)
│       │   └── MultipleGuideLine.java (기존 — Step 4)
│       └── ox/
│           ├── OXFormat.java          (기존 — Step 4)
│           └── OXGuideLine.java       (기존 — Step 4)
├── service/
│   ├── ChatService.java               (기존)
│   ├── FacadeService.java             (기존 — Step 4, Step 5에서는 수정하지 않음)
│   ├── GeminiCacheService.java        (기존 — Step 3+4)
│   ├── GeminiFileService.java         (기존 — Step 2)
│   └── GeminiQuizOrchestrator.java    ← NEW (병렬 파이프라인 조율)
└── util/
    ├── PdfUtils.java                  (기존 — Step 2)
    └── ChunkSplitter.java            ← NEW (Python create_chunks.py 포팅)
```

---

## 배경: 왜 청크를 나누고 병렬로 호출하는가?

LLM에게 "30페이지 PDF에서 15문제 생성"을 한 번에 요청하면:

1. **응답 품질 저하**: 참조 범위가 넓을수록 문제 품질이 떨어진다
2. **토큰 제한**: 한 번의 응답에 15문제를 담으면 max_output_tokens 초과 위험
3. **전체 실패 위험**: 단일 요청 실패 시 전체 결과를 잃는다

청크로 나누면:

1. **범위 한정**: 각 청크가 3~5페이지만 담당 → 문제 품질 향상
2. **토큰 분산**: 각 청크가 1~2문제만 생성 → max_output_tokens 여유
3. **부분 실패 허용**: 10개 중 1개 실패해도 나머지 9개는 유효
4. **병렬 처리**: N개 청크를 동시에 호출 → 총 소요 시간 ≈ 단일 호출 시간

---

## 1단계: `ChunkInfo.java` — 청크 정보 DTO

> 각 청크가 담당하는 페이지와 문제 수를 담는다.

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/dto/ChunkInfo.java`

```java
package com.icc.qasker.ai.dto;

import java.util.List;

public record ChunkInfo(
    List<Integer> referencedPages,
    int quizCount
) {

}
```

---

## 2단계: `ChunkSplitter.java` — 청크 분할 유틸리티

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/util/ChunkSplitter.java`

```java
package com.icc.qasker.ai.util;

import com.icc.qasker.ai.dto.ChunkInfo;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ChunkSplitter {

    /**
     * 페이지 목록과 퀴즈 수를 받아 청크로 분할한다.
     *
     * <p>알고리즘:
     * <ol>
     *   <li>quizCount를 maxChunkCount개의 청크로 라운드 로빈 분배</li>
     *   <li>pageNumbers를 청크 수에 맞게 균등 분할</li>
     *   <li>페이지 3개 미만인 청크는 앞뒤 1페이지씩 여유 확보</li>
     * </ol>
     *
     * @param pageNumbers   참조할 페이지 번호 목록 (정렬된 상태)
     * @param totalQuizCount 총 생성할 문제 수
     * @param maxChunkCount  최대 청크 수
     * @return 분할된 청크 목록
     */
    public static List<ChunkInfo> createPageChunks(
        List<Integer> pageNumbers,
        int totalQuizCount,
        int maxChunkCount
    ) {
        // ──── 1. 청크별 퀴즈 개수 분배 (라운드 로빈) ────
        int[] quizCounts = distributeQuizCounts(totalQuizCount, maxChunkCount);
        int realChunkCount = quizCounts.length;

        // ──── 2. 각 청크에 페이지 균등 할당 ────
        int pageCount = pageNumbers.size();
        int basicPageCountPerChunk = pageCount / realChunkCount;
        int extraPages = pageCount % realChunkCount;

        List<ChunkInfo> chunks = new ArrayList<>(realChunkCount);
        int cursor = 0;

        for (int i = 0; i < realChunkCount; i++) {
            int pagesForThisChunk = basicPageCountPerChunk;
            if (extraPages > 0) {
                pagesForThisChunk++;
                extraPages--;
            }

            // ──── 3. 페이지 3개 미만이면 앞뒤 1페이지 여유 확보 ────
            List<Integer> referencedPages;
            if (pagesForThisChunk < 3) {
                referencedPages = expandToMinThreePages(pageNumbers, cursor);
            } else {
                int end = Math.min(cursor + pagesForThisChunk, pageCount);
                referencedPages = pageNumbers.subList(cursor, end);
            }

            chunks.add(new ChunkInfo(List.copyOf(referencedPages), quizCounts[i]));
            cursor += pagesForThisChunk;
        }

        return chunks;
    }

    /**
     * totalQuizCount를 maxChunkCount 이하의 청크로 라운드 로빈 분배한다.
     *
     * <p>예시: totalQuizCount=7, maxChunkCount=3
     * → [3, 2, 2] (첫 청크부터 나머지 분배)
     *
     * <p>예시: totalQuizCount=3, maxChunkCount=10
     * → [1, 1, 1] (실제 청크 수 = min(totalQuizCount, maxChunkCount))
     */
    private static int[] distributeQuizCounts(int totalQuizCount, int maxChunkCount) {
        int realChunkCount = Math.min(totalQuizCount, maxChunkCount);
        int[] counts = new int[realChunkCount];
        for (int i = 0; i < totalQuizCount; i++) {
            counts[i % realChunkCount]++;
        }
        return counts;
    }

    /**
     * cursor 위치에서 최소 3페이지를 확보한다.
     * Python 원본: 앞뒤 1페이지씩 여유 (cursor == 0이면 앞 3개, 마지막이면 뒤 3개).
     */
    private static List<Integer> expandToMinThreePages(List<Integer> pageNumbers, int cursor) {
        int size = pageNumbers.size();
        if (size <= 3) {
            return pageNumbers;
        }
        if (cursor == 0) {
            return pageNumbers.subList(0, 3);
        }
        if (cursor >= size - 1) {
            return pageNumbers.subList(size - 3, size);
        }
        return pageNumbers.subList(cursor - 1, cursor + 2);
    }
}
```

### 포인트 정리

| 주제                                        | 설명                                                                  |
|-------------------------------------------|---------------------------------------------------------------------|
| **`distributeQuizCounts` 분리**             | record는 불변이므로 먼저 배열로 퀴즈 수를 계산한 뒤 ChunkInfo 생성  |
| **`List.copyOf()`**                       | `subList()`은 원본의 view이므로 방어적 복사                                     |
| **유틸리티 클래스**                              | 상태 없음 → `@Service` 불필요, `private` 생성자로 인스턴스화 방지                     |

### 알고리즘 예시

```
입력: pageNumbers=[1..30], totalQuizCount=15, maxChunkCount=10

1단계: 퀴즈 분배
  15 ÷ 10 = 1 나머지 5
  → [2, 2, 2, 2, 2, 1, 1, 1, 1, 1]  (앞 5개 청크에 +1)

2단계: 페이지 분배
  30 ÷ 10 = 3 페이지/청크, 나머지 0
  → 각 청크 3페이지씩

결과:
  chunk[0]: pages=[1,2,3],    quizCount=2
  chunk[1]: pages=[4,5,6],    quizCount=2
  chunk[2]: pages=[7,8,9],    quizCount=2
  ...
  chunk[9]: pages=[28,29,30], quizCount=1
```

```
입력: pageNumbers=[1..10], totalQuizCount=5, maxChunkCount=10

1단계: 퀴즈 분배
  min(5, 10) = 5 청크
  → [1, 1, 1, 1, 1]

2단계: 페이지 분배
  10 ÷ 5 = 2 페이지/청크, 나머지 0
  → 각 청크 2페이지 → 3개 미만 → 앞뒤 1페이지 여유 확보

결과:
  chunk[0]: pages=[1,2,3],   quizCount=1  (cursor=0 → 앞 3개)
  chunk[1]: pages=[2,3,4],   quizCount=1  (cursor=2 → [1,2,3])
  chunk[2]: pages=[3,4,5],   quizCount=1  (cursor=4 → [3,4,5])
  chunk[3]: pages=[5,6,7],   quizCount=1  (cursor=6 → [5,6,7])
  chunk[4]: pages=[8,9,10],  quizCount=1  (cursor=8 → 뒤 3개)
```

---

## 3단계: `GeminiQuizOrchestrator.java` — 병렬 파이프라인 조율

> Step 2~4의 컴포넌트를 조합하고, Virtual Threads로 청크를 병렬 처리한다.
> `Consumer<AIProblemSet>` 콜백으로 결과를 스트리밍한다.

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/service/GeminiQuizOrchestrator.java`

### 기존 Consumer 패턴 (quiz-impl의 AIServerAdapter)

```java
// AIServerAdapter.java — Python 서버 호출 (기존)
public void streamRequest(
    GenerationRequestToAI request,
    Consumer<ProblemSetGeneratedEvent> onLineReceived   // ← 콜백
) {
    // NDJSON 스트림 → 한 줄씩 파싱 → Consumer 호출
}

// GenerationServiceImpl.java — 호출 측
aiServerAdapter.

streamRequest(request, (quiz) ->{

doMainLogic(request, quiz, emitter, saveProblemSet);
});
```

> GeminiQuizOrchestrator도 **동일한 Consumer 패턴**을 사용한다.
> Step 7에서 `AIServerAdapter` 대신 `GeminiQuizOrchestrator`를 호출하도록 교체한다.

### 구현

```java
package com.icc.qasker.ai.service;

import com.icc.qasker.ai.dto.ChunkInfo;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.ai.AIProblem;
import com.icc.qasker.ai.dto.ai.AIProblemSet;
import com.icc.qasker.ai.dto.ai.AISelection;
import com.icc.qasker.ai.prompt.quiz.common.QuizPromptStrategy;
import com.icc.qasker.ai.prompt.quiz.common.QuizType;
import com.icc.qasker.ai.prompt.quiz.user.UserPrompt;
import com.icc.qasker.ai.util.ChunkSplitter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
public class GeminiQuizOrchestrator {

    private static final int MAX_CHUNK_COUNT = 10;
    private static final int MAX_SELECTION_COUNT = 4;

    private final GeminiFileService geminiFileService;
    private final GeminiCacheService geminiCacheService;
    private final ChatModel chatModel;

    /**
     * PDF에서 퀴즈를 병렬 생성하고, 청크 완료 시마다 Consumer 콜백을 호출한다.
     *
     * <p>파이프라인:
     * <ol>
     *   <li>PDF 업로드 + 처리 대기</li>
     *   <li>캐시 생성 (시스템 프롬프트 + JSON Schema + PDF)</li>
     *   <li>청크 분할</li>
     *   <li>Virtual Thread 풀로 청크별 병렬 호출</li>
     *   <li>완료된 청크마다 후처리 (검증 + 셔플 + 번호 재할당) → 콜백</li>
     * </ol>
     *
     * @param fileUrl     PDF 파일 URL
     * @param strategy    퀴즈 타입별 프롬프트 전략
     * @param quizCount   총 생성할 문제 수
     * @param pageNumbers 참조할 페이지 번호 목록
     * @param onChunkCompleted 청크 완료 시 호출되는 콜백
     */
    public void generateQuizzes(
        String fileUrl,
        QuizPromptStrategy strategy,
        int quizCount,
        List<Integer> pageNumbers,
        Consumer<AIProblemSet> onChunkCompleted
    ) {
        FileMetadata metadata = geminiFileService.uploadPdf(fileUrl);
        log.info("업로드 완료: name={}, uri={}", metadata.name(), metadata.uri());

        BeanOutputConverter<AIProblemSet> converter = new BeanOutputConverter<>(AIProblemSet.class);
        String jsonSchema = converter.getJsonSchema();

        String cacheName = null;

        try {
            cacheName = geminiCacheService.createCache(metadata.uri(), strategy, jsonSchema);
            log.info("캐시 생성 완료: cacheName={}", cacheName);

            // ──── 청크 분할 ────
            List<ChunkInfo> chunks = ChunkSplitter.createPageChunks(
                pageNumbers, quizCount, MAX_CHUNK_COUNT
            );
            log.info("청크 분할 완료: {}개 청크", chunks.size());

            // ──── 병렬 호출 ────
            AtomicInteger numberCounter = new AtomicInteger(1);

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = new ArrayList<>(chunks.size());

                for (ChunkInfo chunk : chunks) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(
                        () -> processChunk(
                            chunk, cacheName, converter, strategy,
                            numberCounter, onChunkCompleted
                        ),
                        executor
                    );
                    futures.add(future);
                }

                // 모든 청크 완료 대기 (부분 실패 허용)
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            }

            log.info("전체 병렬 생성 완료: 총 {}번까지 번호 할당됨",
                numberCounter.get() - 1);
        } finally {
            geminiCacheService.deleteCache(cacheName);
            geminiFileService.deleteFile(metadata.name());
        }
    }

    /**
     * 단일 청크를 처리한다: API 호출 → 검증 → 셔플 → 번호 재할당 → 콜백.
     */
    private void processChunk(
        ChunkInfo chunk,
        String cacheName,
        BeanOutputConverter<AIProblemSet> converter,
        QuizPromptStrategy strategy,
        AtomicInteger numberCounter,
        Consumer<AIProblemSet> onChunkCompleted
    ) {
        try {
            log.debug("청크 처리 시작: pages={}, quizCount={}",
                chunk.referencedPages(), chunk.quizCount());

            // ──── 1. 유저 프롬프트 조립 ────
            String userPrompt = UserPrompt.generate(
                chunk.referencedPages(), chunk.quizCount()
            );

            // ──── 2. ChatModel 호출 (캐시 참조) ────
            ChatResponse response = chatModel.call(
                new Prompt(userPrompt,
                    GoogleGenAiChatOptions.builder()
                        .useCachedContent(true)
                        .cachedContentName(cacheName)
                        .responseMimeType("application/json")
                        .build())
            );

            String jsonText = response.getResult().getOutput().getText();
            log.debug("청크 응답 수신 (길이: {}자)", jsonText.length());

            // ──── 3. JSON → AIProblemSet 역직렬화 ────
            AIProblemSet problemSet = converter.convert(jsonText);

            if (problemSet == null || problemSet.quiz() == null || problemSet.quiz().isEmpty()) {
                log.warn("청크 결과 비어있음: pages={}", chunk.referencedPages());
                return;
            }

            // ──── 4. 선택지 4개 초과 검증 → 폐기 ────
            AIProblem firstProblem = problemSet.quiz().getFirst();
            if (firstProblem.selections() != null
                && firstProblem.selections().size() > MAX_SELECTION_COUNT) {
                log.warn("선택지 초과로 청크 폐기: {}개 선택지, pages={}",
                    firstProblem.selections().size(), chunk.referencedPages());
                return;
            }

            // ──── 5. 후처리 (셔플 + 번호 재할당) ────
            List<AIProblem> processedQuiz = postProcess(
                problemSet.quiz(), strategy, numberCounter
            );

            AIProblemSet result = new AIProblemSet(processedQuiz);

            // ──── 6. 콜백 호출 ────
            onChunkCompleted.accept(result);

            log.debug("청크 처리 완료: pages={}, 문제 {}개",
                chunk.referencedPages(), processedQuiz.size());
        } catch (Exception e) {
            log.error("청크 처리 실패 (계속 진행): pages={}, error={}",
                chunk.referencedPages(), e.getMessage());
            // 부분 실패 허용 — 다른 청크는 계속 진행
        }
    }

    /**
     * 후처리: MULTIPLE/BLANK 타입의 선택지 셔플 + 번호 순차 재할당.
     */
    private List<AIProblem> postProcess(
        List<AIProblem> quiz,
        QuizPromptStrategy strategy,
        AtomicInteger numberCounter
    ) {
        boolean shouldShuffle = strategy == QuizType.MULTIPLE || strategy == QuizType.BLANK;

        List<AIProblem> result = new ArrayList<>(quiz.size());

        for (AIProblem problem : quiz) {
            List<AISelection> selections = problem.selections();

            // 선택지 셔플 (MULTIPLE, BLANK 타입만)
            if (shouldShuffle && selections != null && !selections.isEmpty()) {
                selections = new ArrayList<>(selections);
                Collections.shuffle(selections);
            }

            // 번호 재할당 (AtomicInteger로 스레드 안전)
            int number = numberCounter.getAndIncrement();

            result.add(new AIProblem(number, problem.title(), selections, problem.explanation()));
        }

        return result;
    }
}
```

### 설계 결정

| 결정                                          | 근거                                                             |
|---------------------------------------------|----------------------------------------------------------------|
| **`ExecutorService` + Virtual Threads**     | I/O-bound 작업(Gemini API 호출)에 최적. 플랫폼 스레드 대비 수천 배 가벼움           |
| **`CompletableFuture.runAsync()`**          | `ExecutorService.submit()`보다 합성 가능. `allOf()`로 전체 대기           |
| **`try-with-resources` on ExecutorService** | Java 21의 `AutoCloseable` ExecutorService — 블록 종료 시 자동 shutdown |
| **`AtomicInteger` for 번호 재할당**              | 여러 Virtual Thread에서 동시 접근 → CAS 기반 thread-safe increment       |
| **부분 실패 → `catch` + `return`**              | Python의 `try/except` + `continue` 패턴. 한 청크 실패해도 나머지 계속         |
| **`MAX_CHUNK_COUNT = 10`**                  | Python 환경변수 `MAX_CHUNK_COUNT` 기본값과 동일                          |
| **record 불변성 + 새 인스턴스**                     | `AIProblem`은 record(불변)이므로 셔플/번호 변경 시 새 인스턴스 생성                |

### Virtual Threads 사용 포인트

```java
// Java 21 — Virtual Thread per Task Executor
try(ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()){
    // 각 청크마다 Virtual Thread 1개 생성
    // I/O(Gemini API 호출) 대기 중 OS 스레드를 점유하지 않음
    // 10개 청크 = 10개 Virtual Thread (OS 스레드는 1~2개만 사용)
    }
// try-with-resources 종료 시:
//   executor.shutdown() → 실행 중 태스크 완료 대기 → 종료
```

### ChatModel Thread-Safety

```
Spring AI의 ChatModel 구현체는 stateless하다.
→ 같은 인스턴스를 N개 Virtual Thread에서 동시 호출해도 안전하다.

각 호출은 독립적인 HTTP 요청을 생성하며,
공유 상태(캐시 이름 등)는 읽기 전용으로만 참조한다.
```

---

## 4단계: AIController 수정 — 병렬 생성 테스트 엔드포인트

> Step 4의 `/test-quiz`(단일 호출)에 이어, `/test-parallel-quiz`(병렬 호출) 엔드포인트를 추가한다.

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/controller/AIController.java`

```java
package com.icc.qasker.ai.controller;

import com.icc.qasker.ai.dto.ChatRequest;
import com.icc.qasker.ai.dto.MyChatResponse;
import com.icc.qasker.ai.dto.ai.AIProblemSet;
import com.icc.qasker.ai.prompt.quiz.common.QuizType;
import com.icc.qasker.ai.service.ChatService;
import com.icc.qasker.ai.service.QuizOrchestrationService;
import com.icc.qasker.ai.service.GeminiQuizOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI", description = "AI 관련 API")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AIController {

    private final ChatService chatService;
    private final FacadeService facadeService;
    private final GeminiQuizOrchestrator geminiQuizOrchestrator;

    @Operation(summary = "AI와 채팅한다")
    @PostMapping("/chat")
    public ResponseEntity<MyChatResponse> chat(
        @RequestBody
        ChatRequest request
    ) {
        return ResponseEntity.ok(chatService.chat(request.prompt()));
    }

    @Operation(summary = "PDF로 퀴즈를 생성한다 (Structured Output 테스트)")
    @PostMapping("/test-quiz")
    public ResponseEntity<AIProblemSet> testQuiz(
        @RequestParam String fileUrl,
        @RequestParam QuizType quizType,
        @RequestParam int quizCount,
        @RequestParam List<Integer> pageNumbers
    ) {
        return ResponseEntity.ok(
            facadeService.generateQuiz(fileUrl, quizType, quizCount, pageNumbers)
        );
    }

    @Operation(summary = "PDF로 퀴즈를 병렬 생성한다 (Step 5 테스트)")
    @PostMapping("/test-parallel-quiz")
    public ResponseEntity<AIProblemSet> testParallelQuiz(
        @RequestParam String fileUrl,
        @RequestParam QuizType quizType,
        @RequestParam int quizCount,
        @RequestParam List<Integer> pageNumbers
    ) {
        List<AIProblemSet> collectedResults = Collections.synchronizedList(new ArrayList<>());

        geminiQuizOrchestrator.generateQuizzes(
            fileUrl, quizType, quizCount, pageNumbers,
            collectedResults::add
        );

        // 모든 청크 결과를 하나의 AIProblemSet으로 합침
        List<com.icc.qasker.ai.dto.ai.AIProblem> allQuiz = collectedResults.stream()
            .flatMap(ps -> ps.quiz().stream())
            .toList();

        return ResponseEntity.ok(new AIProblemSet(allQuiz));
    }
}
```

---

## 5단계: 검증

### 5-1. 컴파일 확인

```bash
./gradlew :ai:compileJava
```

### 5-2. 서버 시작

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 5-3. 테스트 호출

```bash
# 병렬 생성 — 객관식 15문제 (30페이지)
curl -X POST "http://localhost:8080/ai/test-parallel-quiz?\
fileUrl=https://files.q-asker.com/실제파일.pdf&\
quizType=MULTIPLE&\
quizCount=15&\
pageNumbers=1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30"

# 병렬 생성 — 빈칸 5문제 (10페이지)
curl -X POST "http://localhost:8080/ai/test-parallel-quiz?\
fileUrl=https://files.q-asker.com/실제파일.pdf&\
quizType=BLANK&\
quizCount=5&\
pageNumbers=1,2,3,4,5,6,7,8,9,10"

# 병렬 생성 — OX 3문제 (5페이지)
curl -X POST "http://localhost:8080/ai/test-parallel-quiz?\
fileUrl=https://files.q-asker.com/실제파일.pdf&\
quizType=OX&\
quizCount=3&\
pageNumbers=1,2,3,4,5"
```

### 기대 로그

```
INFO  GeminiFileService          - PDF 업로드 완료: name=files/r1b5ugz, state=PROCESSING
INFO  GeminiFileService          - 파일 처리 완료: name=files/r1b5ugz, uri=https://...
INFO  GeminiCacheService         - 캐시 생성 완료: name=cachedContents/abc123, ...
INFO  GeminiQuizOrchestrator     - 캐시 생성 완료: cacheName=cachedContents/abc123
INFO  GeminiQuizOrchestrator     - 청크 분할 완료: 10개 청크
DEBUG GeminiQuizOrchestrator     - 청크 처리 시작: pages=[1,2,3], quizCount=2
DEBUG GeminiQuizOrchestrator     - 청크 처리 시작: pages=[4,5,6], quizCount=2
DEBUG GeminiQuizOrchestrator     - 청크 처리 시작: pages=[7,8,9], quizCount=2
  ...                             (10개 청크 병렬 시작)
DEBUG GeminiQuizOrchestrator     - 청크 응답 수신 (길이: 856자)
DEBUG GeminiQuizOrchestrator     - 청크 처리 완료: pages=[4,5,6], 문제 2개
DEBUG GeminiQuizOrchestrator     - 청크 응답 수신 (길이: 912자)
DEBUG GeminiQuizOrchestrator     - 청크 처리 완료: pages=[1,2,3], 문제 2개
  ...                             (완료 순서는 비결정적)
INFO  GeminiQuizOrchestrator     - 전체 병렬 생성 완료: 총 15번까지 번호 할당됨
INFO  GeminiCacheService         - 캐시 삭제 완료: name=cachedContents/abc123
INFO  GeminiFileService          - Gemini 파일 삭제 완료: name=files/r1b5ugz
```

### 검증 체크리스트

| 항목            | 확인 포인트                                      |
|---------------|---------------------------------------------|
| **청크 수**      | quizCount=15, maxChunkCount=10 → 10개 청크 생성  |
| **문제 총 수**    | 응답의 quiz 배열 길이가 15 이하인가? (부분 실패 시 15 미만 가능) |
| **문제 번호**     | 1부터 순차 증가하는가? (중복 없이)                       |
| **선택지 수**     | 각 문제마다 4개 이하인가? (초과 시 해당 청크 폐기됨)            |
| **선택지 순서**    | MULTIPLE/BLANK → 셔플되었는가? (같은 호출 반복 시 순서 변화) |
| **OX 셔플 안 됨** | OX 타입 → 선택지 순서 유지 (O, X)                    |
| **부분 실패**     | 의도적으로 한 청크 실패시켜도 나머지 결과 정상 반환               |
| **병렬 효과**     | Step 4의 단일 호출 대비 소요 시간 비교 (총 시간 ≈ 단일 청크 시간) |
| **리소스 정리**    | 캐시 삭제 로그 + 파일 삭제 로그 확인                      |

---

## Gemini HTTP 메시지 (병렬 호출)

### 공유 캐시 1개 생성 (1회)

```http
POST /v1beta/cachedContents?key={API_KEY} HTTP/1.1

{
  "model": "models/gemini-2.0-flash",
  "systemInstruction": {
    "parts": [{ "text": "당신은 대학 강의노트로부터..." }]
  },
  "contents": [{
    "role": "user",
    "parts": [{
      "fileData": { "fileUri": "https://...files/abc123", "mimeType": "application/pdf" }
    }]
  }],
  "ttl": "600s"
}
```

### 청크별 generateContent (N회 병렬)

```http
── [VT-1] ──────────────────────────────────────────────
POST /v1beta/models/gemini-2.0-flash:generateContent?key={API_KEY}

{
  "cachedContent": "cachedContents/xyz789",
  "contents": [{
    "role": "user",
    "parts": [{ "text": "[생성 지시]\n- 첨부된 강의노트의 [1, 2, 3] 페이지를 참고하여 정확히 2개의 문제를 생성하세요." }]
  }],
  "generationConfig": { "responseMimeType": "application/json" }
}

── [VT-2] (동시) ────────────────────────────────────────
POST /v1beta/models/gemini-2.0-flash:generateContent?key={API_KEY}

{
  "cachedContent": "cachedContents/xyz789",
  "contents": [{
    "role": "user",
    "parts": [{ "text": "[생성 지시]\n- 첨부된 강의노트의 [4, 5, 6] 페이지를 참고하여 정확히 2개의 문제를 생성하세요." }]
  }],
  "generationConfig": { "responseMimeType": "application/json" }
}

── [VT-3] (동시) ────────────────────────────────────────
...
```

> 모든 청크가 **같은 `cachedContent`를 참조**한다.
> PDF와 시스템 프롬프트는 캐시에 포함되어 있으므로, 유저 프롬프트만 전송한다.

---

## 이후 Step 프리뷰

### Step 6: Rate Limiter + 에러 처리

```java
// GeminiQuizOrchestrator에 추가될 내용:

// Rate Limiter — 청크 수만큼 소모
rateLimiter.tryConsume(chunks.size());

// Circuit Breaker — Gemini API 장애 시 빠른 실패
@CircuitBreaker(name = "gemini", fallbackMethod = "fallback")
```

### Step 7: quiz-impl 연결

```java
// GenerationServiceImpl에서 GeminiQuizOrchestrator 호출:

geminiQuizOrchestrator.generateQuizzes(
    fileUrl, quizType, quizCount, pageNumbers,
    (problemSet) -> { /* AIProblemSet → ProblemSetGeneratedEvent 변환 */ }
);
```

---

## 참고 링크

- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444) — Java 21 Virtual Threads 공식 스펙
- [Executors.newVirtualThreadPerTaskExecutor() Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html#newVirtualThreadPerTaskExecutor())
- [CompletableFuture Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Spring AI — Structured Output Converter](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
- [Gemini API — Context Caching](https://ai.google.dev/gemini-api/docs/caching)
