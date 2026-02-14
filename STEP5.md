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
modules/ai/
├── api/src/main/java/com/icc/qasker/ai/
│   ├── QuizOrchestrationService.java              (기존 — 인터페이스)
│   ├── GeminiCacheService.java                    (기존 — 인터페이스)
│   ├── GeminiFileService.java                     (기존 — 인터페이스)
│   └── dto/
│       ├── AIProblemSet.java                      ← NEW (순수 DTO)
│       ├── AIProblem.java                         ← NEW (순수 DTO)
│       ├── AISelection.java                       ← NEW (순수 DTO)
│       ├── ChunkInfo.java                         ← NEW (청크 정보 record)
│       └── GeminiFileUploadResponse.java          (기존 — Step 2)
│
└── impl/src/main/java/com/icc/qasker/ai/
    ├── config/
    │   ├── GeminiCacheConfig.java                 (기존 — Step 3)
    │   └── GeminiFileRestClientConfig.java        (기존 — Step 2)
    ├── controller/
    │   └── AIController.java                      (수정 — 병렬 생성 테스트 엔드포인트)
    ├── structure/
    │   ├── GeminiProblemSet.java                   ← 기존 AIProblemSet 이름 변경
    │   ├── GeminiProblem.java                      ← 기존 AIProblem 이름 변경
    │   └── GeminiSelection.java                    ← 기존 AISelection 이름 변경
    ├── prompt/
    │   └── quiz/
    │       ├── common/
    │       │   ├── QuizPromptStrategy.java        (기존 — Step 4)
    │       │   └── QuizType.java                  (기존 — Step 4)
    │       ├── system/
    │       │   └── SystemPrompt.java              (기존 — Step 4)
    │       ├── user/
    │       │   └── UserPrompt.java                (기존 — Step 4)
    │       ├── blank/
    │       │   ├── BlankFormat.java               (기존 — Step 4)
    │       │   └── BlankGuideLine.java            (기존 — Step 4)
    │       ├── mutiple/
    │       │   ├── MultipleFormat.java            (기존 — Step 4)
    │       │   └── MultipleGuideLine.java         (기존 — Step 4)
    │       └── ox/
    │           ├── OXFormat.java                  (기존 — Step 4)
    │           └── OXGuideLine.java               (기존 — Step 4)
    ├── mapper/
    │   └── GeminiProblemSetMapper.java             ← NEW (structure → dto 변환)
    ├── service/
    │   ├── ChatService.java                       (기존)
    │   ├── GeminiCacheServiceImpl.java            (기존 — Step 3+4)
    │   ├── GeminiFileServiceImpl.java             (기존 — Step 2)
    │   └── QuizOrchestrationServiceImpl.java      ← 수정 (병렬 파이프라인)
    └── util/
        ├── PdfUtils.java                          (기존 — Step 2)
        └── ChunkSplitter.java                     ← NEW (Python create_chunks.py 포팅)
```

---

## 사전 작업: AI 응답 타입을 api 계약과 impl 내부 구조로 분리

> `impl/structure/`의 Gemini 전용 record는 그대로 두고,
> `api/dto/`에 순수 DTO를 새로 정의한다.

### 현재 상태와 문제

```
modules/ai/impl/src/.../ai/structure/
├── AIProblemSet.java      ← @JsonPropertyDescription 포함 (BeanOutputConverter용)
├── AIProblem.java         ← 동일
└── AISelection.java       ← 동일
```

이 record들에는 `@JsonPropertyDescription`이 붙어 있다.
이 어노테이션은 Spring AI의 `BeanOutputConverter`가 Gemini에 보낼 **JSON Schema를 자동 생성**할 때 사용한다.

```java
// 현재 AIProblem.java — Gemini 전용 어노테이션
@JsonPropertyDescription("문제 번호 (1부터 시작)")
int number,
@JsonPropertyDescription("선택지 목록")
List<AISelection> selections,
```

이것은 **Gemini API 구현의 내부 디테일**이지, 모듈 간 계약이 아니다.

한편 `QuizOrchestrationService` 인터페이스는 `ai-api` 모듈에 있고,
콜백 시그니처가 `Consumer<AIProblemSet>`이므로 `AIProblemSet` 타입이 `ai-api`에 있어야 한다.

```
quiz-impl ──depends──▶ ai-api ◀──depends── ai-impl
                          │
                          └─ QuizOrchestrationService
                               Consumer<AIProblemSet>  ← 이 타입이 ai-api에 필요

quiz-impl ──✕──▶ ai-impl  (이 의존성은 없고, 있어서도 안 된다)
```

### 해결: 역할별로 타입을 분리한다

| 위치                   | 타입                                                     | 역할                     | `@JsonPropertyDescription` |
|----------------------|--------------------------------------------------------|------------------------|----------------------------|
| `ai-api/dto/`        | `AIProblemSet`, `AIProblem`, `AISelection`             | 모듈 간 계약 (순수 DTO)       | 없음                         |
| `ai-impl/structure/` | `GeminiProblemSet`, `GeminiProblem`, `GeminiSelection` | Gemini JSON Schema 생성용 | 있음                         |

### api: 순수 DTO (모듈 간 계약)

**경로**: `modules/ai/api/src/main/java/com/icc/qasker/ai/dto/`

```java
// 어노테이션 없는 순수 record — api 모듈에 추가 의존성 불필요
package com.icc.qasker.ai.dto;

public record AIProblemSet(List<AIProblem> quiz) {

}

public record AIProblem(int number, String title, List<AISelection> selections,
                        String explanation) {

}

public record AISelection(String content, boolean correct) {

}
```

### impl: Gemini 전용 structure (BeanOutputConverter용)

**경로**: `modules/ai/impl/src/main/java/com/icc/qasker/ai/structure/` (기존 유지, 이름만 변경)

```java
// @JsonPropertyDescription 포함 — BeanOutputConverter가 JSON Schema 생성에 사용
package com.icc.qasker.ai.structure;
    
public record GeminiProblemSet(
    @JsonPropertyDescription("생성된 퀴즈 문제 목록")
    List<GeminiProblem> quiz
) {

}

public record GeminiProblem(
    @JsonPropertyDescription("문제 번호 (1부터 시작)")
    int number,
    @JsonPropertyDescription("문제 본문")
    String title,
    @JsonPropertyDescription("선택지 목록")
    List<GeminiSelection> selections,
    @JsonPropertyDescription("정답 해설")
    String explanation
) {

}

public record GeminiSelection(
    @JsonPropertyDescription("선택지 텍스트")
    String content,
    @JsonPropertyDescription("정답 여부 (정답이면 true, 오답이면 false)")
    boolean correct
) {

}
```

### impl 내부에서 변환: `GeminiProblemSetMapper`

**경로**: `modules/ai/impl/src/main/java/com/icc/qasker/ai/mapper/GeminiProblemSetMapper.java`

변환 로직을 `mapper` 패키지로 분리한다.
서비스에서는 `GeminiProblemSetMapper.toDto()`만 호출한다.

```java
package com.icc.qasker.ai.mapper;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.prompt.quiz.common.QuizType;
import com.icc.qasker.ai.structure.GeminiProblem;
import com.icc.qasker.ai.structure.GeminiProblemSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GeminiProblemSetMapper {

    /**
     * GeminiProblemSet → AIProblemSet 변환 + 선택지 셔플 + 번호 재할당.
     *
     * @param source        Gemini 응답 역직렬화 결과
     * @param strategyValue 퀴즈 타입 문자열 (MULTIPLE, BLANK, OX)
     * @param numberCounter 스레드 안전 번호 카운터
     * @return 변환된 AIProblemSet
     */
    public static AIProblemSet toDto(
        GeminiProblemSet source,
        String strategyValue,
        AtomicInteger numberCounter
    ) {
        QuizType quizType = QuizType.valueOf(strategyValue);
        boolean shouldShuffle = quizType == QuizType.MULTIPLE || quizType == QuizType.BLANK;

        List<AIProblem> result = new ArrayList<>(source.quiz().size());

        for (GeminiProblem problem : source.quiz()) {
            List<AISelection> selections = mapSelections(problem);

            if (shouldShuffle && !selections.isEmpty()) {
                selections = new ArrayList<>(selections);
                Collections.shuffle(selections);
            }

            int number = numberCounter.getAndIncrement();
            result.add(new AIProblem(number, problem.title(), selections, problem.explanation()));
        }

        return new AIProblemSet(result);
    }

    private static List<AISelection> mapSelections(GeminiProblem problem) {
        if (problem.selections() == null) {
            return List.of();
        }
        return problem.selections().stream()
            .map(gs -> new AISelection(gs.content(), gs.correct()))
            .toList();
    }
}
```

서비스에서의 호출:

```java
// QuizOrchestrationServiceImpl.processChunk() 내부
GeminiProblemSet geminiResult = converter.convert(jsonText);

AIProblemSet result = GeminiProblemSetMapper.toDto(
    geminiResult, strategyValue, numberCounter
);

onChunkCompleted.accept(result);
```

### 분리 후 구조

```
modules/ai/api/src/.../ai/
├── QuizOrchestrationService.java          (인터페이스)
├── GeminiCacheService.java                (인터페이스)
├── GeminiFileService.java                 (인터페이스)
└── dto/
    ├── AIProblemSet.java                  ← NEW (순수 DTO)
    ├── AIProblem.java                     ← NEW (순수 DTO)
    ├── AISelection.java                   ← NEW (순수 DTO)
    ├── ChunkInfo.java                     (기존)
    └── GeminiFileUploadResponse.java      (기존)
```

```
modules/ai/impl/src/.../ai/
├── mapper/
│   └── GeminiProblemSetMapper.java        ← NEW (structure → dto 변환)
├── service/
│   ├── QuizOrchestrationServiceImpl.java  (Mapper 호출로 변환 위임)
│   ├── GeminiCacheServiceImpl.java
│   └── GeminiFileServiceImpl.java
├── structure/
│   ├── GeminiProblemSet.java              ← 기존 AIProblemSet 이름 변경
│   ├── GeminiProblem.java                 ← 기존 AIProblem 이름 변경
│   └── GeminiSelection.java              ← 기존 AISelection 이름 변경
├── ...
```

### 왜 이렇게 분리하는가?

```
@JsonPropertyDescription가 붙은 record는:
  → BeanOutputConverter가 Gemini에 보낼 JSON Schema를 만들 때만 쓰인다
  → Gemini 구현의 내부 디테일이다
  → ai-impl 안에 있어야 한다

Consumer<AIProblemSet> 콜백으로 전달되는 record는:
  → quiz-impl이 참조해야 한다
  → 모듈 간 계약이다
  → ai-api에 있어야 한다

두 역할을 하나의 타입으로 합치면:
  → api 모듈에 jackson-annotations 의존성을 추가해야 한다
  → 순수 DTO에 Gemini 전용 어노테이션이 섞인다
  → 역할이 불명확해진다
```

### 변경 사항 요약

| 파일                                      | 작업                         | 비고                                  |
|-----------------------------------------|----------------------------|-------------------------------------|
| `api/dto/AIProblemSet.java`             | 신규 생성                      | 순수 record, 어노테이션 없음                 |
| `api/dto/AIProblem.java`                | 신규 생성                      | 동일                                  |
| `api/dto/AISelection.java`              | 신규 생성                      | 동일                                  |
| `impl/structure/AIProblemSet.java`      | `GeminiProblemSet`으로 이름 변경 | `@JsonPropertyDescription` 유지       |
| `impl/structure/AIProblem.java`         | `GeminiProblem`으로 이름 변경    | 동일                                  |
| `impl/structure/AISelection.java`       | `GeminiSelection`으로 이름 변경  | 동일                                  |
| `impl/mapper/GeminiProblemSetMapper.java` | 신규 생성                      | `GeminiProblemSet` → `AIProblemSet` 변환 + 셔플 + 번호 할당 |
| `QuizOrchestrationServiceImpl.java`     | 변환 로직을 Mapper로 위임          | `GeminiProblemSetMapper.toDto()` 호출 |
| `ai-api/build.gradle`                   | 변경 없음                      | 추가 의존성 불필요                          |

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

| 주제                            | 설명                                              |
|-------------------------------|-------------------------------------------------|
| **`distributeQuizCounts` 분리** | record는 불변이므로 먼저 배열로 퀴즈 수를 계산한 뒤 ChunkInfo 생성   |
| **`List.copyOf()`**           | `subList()`은 원본의 view이므로 방어적 복사                 |
| **유틸리티 클래스**                  | 상태 없음 → `@Service` 불필요, `private` 생성자로 인스턴스화 방지 |

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

## 3단계: `QuizOrchestrationServiceImpl.java` — 병렬 파이프라인 조율

> `QuizOrchestrationService` 인터페이스를 구현한다.
> Step 2~4의 컴포넌트를 조합하고, Virtual Threads로 청크를 병렬 처리한다.
> `Consumer<AIProblemSet>` 콜백으로 결과를 스트리밍한다.

**경로**: `modules/ai/impl/src/main/java/com/icc/qasker/ai/service/QuizOrchestrationServiceImpl.java`

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

> QuizOrchestrationServiceImpl도 **동일한 Consumer 패턴**을 사용한다.
> Step 7에서 `AIServerAdapter` 대신 `QuizOrchestrationServiceImpl`를 호출하도록 교체한다.

### 구현

```java
package com.icc.qasker.ai.service;

import com.icc.qasker.ai.GeminiCacheService;
import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.ChunkInfo;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.mapper.GeminiProblemSetMapper;
import com.icc.qasker.ai.prompt.quiz.user.UserPrompt;
import com.icc.qasker.ai.structure.GeminiProblem;
import com.icc.qasker.ai.structure.GeminiProblemSet;
import com.icc.qasker.ai.util.ChunkSplitter;
import java.util.ArrayList;
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
public class QuizOrchestrationServiceImpl implements QuizOrchestrationService {

    private static final int MAX_CHUNK_COUNT = 10;
    private static final int MAX_SELECTION_COUNT = 4;

    private final GeminiFileService geminiFileService;
    private final GeminiCacheService geminiCacheService;
    private final ChatModel chatModel;

    @Override
    public void generateQuiz(
        String fileUrl,
        String strategyValue,
        int quizCount,
        List<Integer> referencePages,
        Consumer<AIProblemSet> onChunkCompleted
    ) {
        FileMetadata metadata = geminiFileService.uploadPdf(fileUrl);
        log.info("업로드 완료: name={}, uri={}", metadata.name(), metadata.uri());

        var converter = new BeanOutputConverter<>(GeminiProblemSet.class);
        String jsonSchema = converter.getJsonSchema();

        String cacheName = geminiCacheService.createCache(metadata.uri(), strategyValue, jsonSchema);
        try {
            log.info("캐시 생성 완료: cacheName={}", cacheName);

            List<ChunkInfo> chunks = ChunkSplitter.createPageChunks(
                referencePages, quizCount, MAX_CHUNK_COUNT
            );
            log.info("청크 분할 완료: {}개 청크", chunks.size());

            AtomicInteger numberCounter = new AtomicInteger(1);

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = new ArrayList<>(chunks.size());

                for (ChunkInfo chunk : chunks) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(
                        () -> processChunk(
                            chunk, cacheName, converter, strategyValue, numberCounter,
                            onChunkCompleted
                        ),
                        executor
                    );
                    futures.add(future);
                }

                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            }
            log.info("전체 병렬 생성 완료: 총 {}번까지 번호 할당됨", numberCounter.get() - 1);
        } finally {
            geminiCacheService.deleteCache(cacheName);
            geminiFileService.deleteFile(metadata.name());
        }
    }

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
            log.debug("청크 응답 수신 (길이: {}자)", jsonText.length());

            GeminiProblemSet geminiResult = converter.convert(jsonText);

            if (geminiResult == null || geminiResult.quiz() == null
                || geminiResult.quiz().isEmpty()) {
                log.warn("청크 결과 비어있음: pages={}", chunk.referencedPages());
                return;
            }

            GeminiProblem firstProblem = geminiResult.quiz().getFirst();
            if (firstProblem.selections() != null
                && firstProblem.selections().size() > MAX_SELECTION_COUNT) {
                log.warn("선택지 초과로 청크 폐기: {}개 선택지, pages={}",
                    firstProblem.selections().size(), chunk.referencedPages());
                return;
            }

            // ──── Mapper로 변환 위임 (structure → dto + 셔플 + 번호 할당) ────
            AIProblemSet result = GeminiProblemSetMapper.toDto(
                geminiResult, strategyValue, numberCounter
            );

            onChunkCompleted.accept(result);

            log.debug("청크 처리 완료: pages={}, 문제 {}개",
                chunk.referencedPages(), result.quiz().size());
        } catch (Exception e) {
            log.error("청크 처리 실패 (계속 진행): pages={}, error={}",
                chunk.referencedPages(), e.getMessage());
        }
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
| **record 불변성 + 새 인스턴스**                     | `GeminiProblem`은 record(불변)이므로 변환+셔플+번호 변경 시 새 `AIProblem` 인스턴스 생성 |
| **`GeminiProblemSetMapper` 분리**              | 변환 로직을 `mapper` 패키지로 분리하여 서비스의 책임을 줄이고 테스트 용이성 확보           |

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

import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.ChatRequest;
import com.icc.qasker.ai.dto.MyChatResponse;
import com.icc.qasker.ai.prompt.quiz.common.QuizType;
import com.icc.qasker.ai.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.Collections;
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
    private final QuizOrchestrationService quizOrchestrationService;

    @Operation(summary = "AI와 채팅한다")
    @PostMapping("/chat")
    public ResponseEntity<MyChatResponse> chat(
        @RequestBody
        ChatRequest request
    ) {
        return ResponseEntity.ok(chatService.chat(request.prompt()));
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

        quizOrchestrationService.generateQuiz(
            fileUrl, quizType.name(), quizCount, pageNumbers,
            collectedResults::add
        );

        // 모든 청크 결과를 하나의 AIProblemSet으로 합침
        List<AIProblem> allQuiz = collectedResults.stream()
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
INFO  QuizOrchestrationServiceImpl     - 캐시 생성 완료: cacheName=cachedContents/abc123
INFO  QuizOrchestrationServiceImpl     - 청크 분할 완료: 10개 청크
DEBUG QuizOrchestrationServiceImpl     - 청크 처리 시작: pages=[1,2,3], quizCount=2
DEBUG QuizOrchestrationServiceImpl     - 청크 처리 시작: pages=[4,5,6], quizCount=2
DEBUG QuizOrchestrationServiceImpl     - 청크 처리 시작: pages=[7,8,9], quizCount=2
  ...                             (10개 청크 병렬 시작)
DEBUG QuizOrchestrationServiceImpl     - 청크 응답 수신 (길이: 856자)
DEBUG QuizOrchestrationServiceImpl     - 청크 처리 완료: pages=[4,5,6], 문제 2개
DEBUG QuizOrchestrationServiceImpl     - 청크 응답 수신 (길이: 912자)
DEBUG QuizOrchestrationServiceImpl     - 청크 처리 완료: pages=[1,2,3], 문제 2개
  ...                             (완료 순서는 비결정적)
INFO  QuizOrchestrationServiceImpl     - 전체 병렬 생성 완료: 총 15번까지 번호 할당됨
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
// QuizOrchestrationServiceImpl에 추가될 내용:

// Rate Limiter — 청크 수만큼 소모
rateLimiter.tryConsume(chunks.size());

// Circuit Breaker — Gemini API 장애 시 빠른 실패
@CircuitBreaker(name = "gemini", fallbackMethod = "fallback")
```

### Step 7: quiz-impl 연결

```java
// GenerationServiceImpl에서 QuizOrchestrationService 호출:

quizOrchestrationService.generateQuiz(
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
