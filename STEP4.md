# Step 4: 프롬프트 시스템 + Structured Output

> Python 서버의 프롬프트 템플릿과 JSON 구조 강제를 Java로 포팅한다.
> LLM 응답을 `AIProblemSet` Java 객체로 자동 역직렬화하는 Structured Output 파이프라인을 구축한다.

---

## 전체 플로우

```
fileUrl (PDF 경로)
       │
       ▼
┌──────────────────────────────────────────────────────────────────┐
│  Step 4에서 구현하는 영역                                           │
│                                                                  │
│  0. BeanOutputConverter<AIProblemSet>.getFormat()                 │
│     └ AIProblemSet 클래스에서 JSON Schema 자동 생성                 │
│                                                                  │
│  1. SystemPrompt.generate(strategy, jsonSchema)                  │
│     └ 캐시 생성 시 systemInstruction으로 포함 (JSON Schema 포함)     │
│                                                                  │
│  2. GeminiCacheService.createCache(fileUri, strategy, jsonSchema) │
│     └ PDF + 시스템 프롬프트 + JSON Schema를 캐시에 함께 저장           │
│                                                                  │
│  3. UserPrompt.generate(pageNumbers, quizCount)                  │
│     └ 호출마다 동적 파라미터로 조립                                   │
│                                                                  │
│  4. chatModel.call(userPrompt, options {                         │
│         cachedContentName, responseMimeType: "application/json"  │
│     })                                                           │
│                                                                  │
│  5. BeanOutputConverter.convert(responseText)                    │
│     └ JSON → AIProblemSet 자동 역직렬화                            │
└──────────────────────────────────────────────────────────────────┘
       │
       ▼
AIProblemSet { quiz: [AIProblem, AIProblem, ...] }
→ 이후 Step 5에서 병렬 배치 생성 시 사용
```

### 시스템 프롬프트의 캐시 내재화

```
캐시 = PDF + 시스템 프롬프트 + JSON Schema(systemInstruction)
chatModel.call(유저 프롬프트만, 캐시 참조)
→ 시스템 프롬프트와 JSON Schema는 캐시에 포함되어 재전송 불필요
→ Step 5 병렬 배치 시 토큰 절약
```

---

## 완성 후 디렉토리 구조

```
modules/ai/src/main/java/com/icc/qasker/ai/
├── config/
│   ├── GeminiCacheConfig.java          (기존 — Step 3)
│   └── GeminiFileRestClientConfig.java (기존 — Step 2)
├── controller/
│   └── AIController.java               (수정 — 테스트 엔드포인트)
├── dto/
│   ├── ai/
│   │   ├── AIProblemSet.java          ← NEW (Structured Output 루트 DTO)
│   │   ├── AIProblem.java             ← NEW (개별 문제 DTO)
│   │   └── AISelection.java          ← NEW (선택지 DTO)
│   ├── ChatRequest.java               (기존)
│   ├── GeminiFileUploadResponse.java   (기존 — Step 2)
│   └── MyChatResponse.java            (기존)
├── prompt/
│   └── quiz/
│       ├── common/
│       │   ├── QuizPromptStrategy.java ← NEW (프롬프트 전략 인터페이스)
│       │   └── QuizType.java          ← NEW (타입별 프롬프트 enum)
│       ├── system/
│       │   └── SystemPrompt.java      ← NEW (시스템 프롬프트 — 캐시에 포함)
│       ├── user/
│       │   └── UserPrompt.java        ← NEW (유저 프롬프트 — 호출마다 전송)
│       ├── blank/
│       │   ├── BlankFormat.java       ← NEW (빈칸 출력 형식)
│       │   └── BlankGuideLine.java    ← NEW (빈칸 작성 지침)
│       ├── mutiple/
│       │   ├── MultipleFormat.java    ← NEW (객관식 출력 형식)
│       │   └── MultipleGuideLine.java ← NEW (객관식 작성 지침)
│       └── ox/
│           ├── OXFormat.java          ← NEW (OX 출력 형식)
│           └── OXGuideLine.java       ← NEW (OX 작성 지침)
├── service/
│   ├── ChatService.java               (기존)
│   ├── FacadeService.java             (수정 — Structured Output 통합)
│   ├── GeminiCacheService.java        (수정 — systemInstruction 추가)
│   └── GeminiFileService.java         (기존 — Step 2)
└── util/
    └── PdfUtils.java                  (기존 — Step 2)
```

---

## 배경: Structured Output이란?

LLM에게 "객관식 문제 5개 만들어줘"라고 하면 응답 형식이 매번 다르다. Java 코드에서 이를 안정적으로 파싱할 수 없다.

**Gemini API**와 **Spring AI**를 결합하면 응답 형식을 **강제**할 수 있다:

1. Java 클래스 정의 → `BeanOutputConverter`가 JSON Schema 자동 생성
2. `BeanOutputConverter.getFormat()` → JSON Schema를 시스템 프롬프트에 포함 → LLM이 정확한 키/구조 인지
3. `responseMimeType: "application/json"` → Gemini API 레벨에서 JSON 응답 강제
4. `BeanOutputConverter.convert(jsonText)` → JSON 문자열을 Java 객체로 자동 변환

> Python 서버는 OpenAI의 `response_format: { type: "json_schema" }`로 구조를 강제했다.
> Spring AI에서는 `BeanOutputConverter` + `responseMimeType`으로 동일한 효과를 달성한다.

---

## 1단계: DTO 생성 — AIProblemSet / AIProblem / AISelection

> BeanOutputConverter가 JSON Schema를 생성할 수 있도록, Gemini 응답 구조를 Java 클래스로 정의한다.

### 기존 DTO와의 관계

```
ai 모듈 (Step 4)                     quiz-api 모듈 (기존)

AIProblemSet                          ProblemSetGeneratedEvent
  └ quiz: List<AIProblem>               └ quiz: List<QuizGeneratedFromAI>
      ├ number                              ├ number
      ├ title                               ├ title
      ├ selections: List<AISelection>       ├ selections: List<SelectionsOfAI>
      │   ├ content                         │   ├ content
      │   └ correct                         │   └ correct
      └ explanation                         └ explanation

→ 구조가 거의 동일. Step 7에서 단순 필드 복사 매핑.
→ ai 모듈은 quiz-api에 의존하지 않으므로 독립 DTO를 사용한다.
```

### 1-1. `AISelection.java`

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/dto/ai/AISelection.java`

```java
package com.icc.qasker.ai.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record AISelection(
    @JsonPropertyDescription("선택지 텍스트")
    String content,

    @JsonPropertyDescription("정답 여부 (정답이면 true, 오답이면 false)")
    boolean correct
) {

}
```

### 1-2. `AIProblem.java`

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/dto/ai/AIProblem.java`

```java
package com.icc.qasker.ai.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record AIProblem(
    @JsonPropertyDescription("문제 번호 (1부터 시작)")
    int number,

    @JsonPropertyDescription("문제 본문")
    String title,

    @JsonPropertyDescription("선택지 목록")
    List<AISelection> selections,

    @JsonPropertyDescription("정답 해설")
    String explanation
) {

}
```

### 1-3. `AIProblemSet.java`

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/dto/ai/AIProblemSet.java`

```java
package com.icc.qasker.ai.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record AIProblemSet(

    @JsonPropertyDescription("생성된 퀴즈 문제 목록")
    List<AIProblem> quiz

) {

}
```

### 포인트 정리

| 주제                             | 설명                                                                   |
|--------------------------------|----------------------------------------------------------------------|
| **record 사용**                  | 불변 DTO + 자동 생성자/getter. Jackson 2.12+가 record 역직렬화를 기본 지원            |
| **`@JsonPropertyDescription`** | BeanOutputConverter가 JSON Schema의 `description`으로 변환. LLM에게 필드 의미 전달 |
| **`dto.ai` 서브패키지**             | AI 응답 전용 DTO를 별도 패키지로 분리                                             |

---

## 2단계: 프롬프트 구조 — Strategy 패턴 + 타입별 상수

> Python 서버의 `prompt/core/multiple.py`, `blank.py`, `ox.py`에 대응.
> Strategy 인터페이스 + enum으로 타입별 프롬프트를 매핑한다.

### Python → Java 매핑

| 역할            | Python                      | Java                                           |
|---------------|-----------------------------|------------------------------------------------|
| 타입별 생성 지침     | `prompt/core/multiple.py` 등 | `prompt/quiz/mutiple/MultipleGuideLine.java` 등 |
| 타입별 출력 형식     | `prompt/core/multiple.py` 등 | `prompt/quiz/mutiple/MultipleFormat.java` 등    |
| 프롬프트 전략 인터페이스 | (없음)                        | `QuizPromptStrategy` 인터페이스                     |
| 타입 → 프롬프트 매핑  | `prompt_factory.py`의 분기     | `QuizType` enum (Strategy 구현)                  |
| 시스템 프롬프트 조립   | `generate_service.py`       | `SystemPrompt.java` (캐시에 포함)                   |
| 유저 프롬프트 조립    | `generate_service.py`       | `UserPrompt.java` (호출마다 전송)                    |

### 2-1. 타입별 프롬프트 상수 — `prompt/quiz` 패키지

```
prompt/quiz/
├── common/
│   ├── QuizPromptStrategy.java  ← 전략 인터페이스
│   └── QuizType.java            ← enum (인터페이스 구현)
├── system/
│   └── SystemPrompt.java        ← 시스템 프롬프트 (캐시에 포함)
├── user/
│   └── UserPrompt.java          ← 유저 프롬프트 (호출마다 전송)
├── blank/
│   ├── BlankGuideLine.java      ← 빈칸 작성 지침
│   └── BlankFormat.java         ← 빈칸 출력 형식
├── mutiple/
│   ├── MultipleGuideLine.java   ← 객관식 작성 지침
│   └── MultipleFormat.java      ← 객관식 출력 형식
└── ox/
    ├── OXGuideLine.java         ← OX 작성 지침
    └── OXFormat.java            ← OX 출력 형식
```

타입별 상수 클래스는 모두 동일한 구조를 따른다:

```java

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlankGuideLine {

    public static final String content = """
        ... 프롬프트 텍스트 ...
        """;
}
```

### 2-2. `QuizPromptStrategy` 인터페이스

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/prompt/quiz/common/QuizPromptStrategy.java`

```java
package com.icc.qasker.ai.prompt.quiz.common;

public interface QuizPromptStrategy {

    String getFormat();

    String getGuideLine();
}
```

> `GeminiCacheService`와 `SystemPrompt`가 특정 enum에 직접 의존하지 않도록 인터페이스를 도입한다.

### 2-3. `QuizType` enum

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/prompt/quiz/common/QuizType.java`

```java
package com.icc.qasker.ai.prompt.quiz.common;

import com.icc.qasker.ai.prompt.quiz.blank.BlankFormat;
import com.icc.qasker.ai.prompt.quiz.blank.BlankGuideLine;
import com.icc.qasker.ai.prompt.quiz.mutiple.MultipleFormat;
import com.icc.qasker.ai.prompt.quiz.mutiple.MultipleGuideLine;
import com.icc.qasker.ai.prompt.quiz.ox.OXFormat;
import com.icc.qasker.ai.prompt.quiz.ox.OXGuideLine;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuizType implements QuizPromptStrategy {
    MULTIPLE(MultipleGuideLine.content, MultipleFormat.content),
    BLANK(BlankGuideLine.content, BlankFormat.content),
    OX(OXGuideLine.content, OXFormat.content);

    private final String guideLine;
    private final String format;
}
```

### 왜 ai 모듈에 자체 enum인가?

```
quiz-impl ──depends──▶ quiz-api (QuizType enum 정의)
quiz-impl ──depends──▶ ai      (캐시+프롬프트 호출)     ← Step 7에서 추가
ai        ──depends──▶ global  (에러 처리)

ai 모듈은 quiz-api를 의존하지 않는다.
따라서 quiz-api의 QuizType을 직접 참조할 수 없다.

Step 7 호출 측:
  ai.QuizType type = ai.QuizType.valueOf(quizApiQuizType.name());
  // quiz-api.QuizType.MULTIPLE → "MULTIPLE" → ai.QuizType.MULTIPLE
```

---

## 3단계: 프롬프트 조립 — `SystemPrompt` + `UserPrompt`

> Python 서버의 `generate_service.py:50-63`에 대응.
> **시스템 프롬프트**(캐시에 포함)와 **유저 프롬프트**(호출마다 전송)를 분리한다.

### 분리 구조

```
SystemPrompt.generate(strategy, jsonSchema)
  → 캐시의 systemInstruction으로 포함
  → 한번만 생성, 모든 청크가 공유

  구성:
  ┌──────────────────────────────────────────────────────────┐
  │ BASE_INSTRUCTION (역할 + 작성 규칙)                        │
  │ ========================================                  │
  │ SECTION 1: OUTPUT FORMAT (strategy.getFormat())           │
  │ ========================================                  │
  │ SECTION 2: CONTENT GUIDELINES (strategy.getGuideLine())   │
  │ ========================================                  │
  │ SECTION 3: JSON RESPONSE SCHEMA (jsonSchema)              │
  │   → BeanOutputConverter.getFormat()이 생성한 JSON Schema    │
  │   → LLM이 정확한 키 이름과 구조로 응답하도록 강제              │
  └──────────────────────────────────────────────────────────┘

UserPrompt.generate(pageNumbers, quizCount)
  → chatModel.call()마다 전송
  → 청크별로 다른 페이지 번호와 문제 수

  구성:
  ┌──────────────────────────────────────────────────────┐
  │ [생성 지시]                                            │
  │ - 첨부된 강의노트의 {pages} 페이지를 참고하여              │
  │   정확히 {count}개의 문제를 생성하세요.                    │
  └──────────────────────────────────────────────────────┘
```

### 3-1. `SystemPrompt.java`

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/prompt/quiz/system/SystemPrompt.java`

```java
package com.icc.qasker.ai.prompt.quiz.system;

import com.icc.qasker.ai.prompt.quiz.common.QuizPromptStrategy;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SystemPrompt {

    private static final String BASE_INSTRUCTION = """
        당신은 대학 강의노트로부터 평가용 퀴즈를 생성하는 AI입니다.
        
        [작성 규칙]
        - 모든 텍스트는 한국어로 작성한다.
        - 개행을 적절히 사용하여 가독성을 확보한다.
        - "강의노트에 따르면", "교재에 의하면" 같은 출처 언급을 하지 않는다.
        - 문제와 해설은 강의노트의 내용을 기반으로 하되, 독립적으로 이해 가능하게 작성한다.
        """;


    public static String generate(QuizPromptStrategy strategy, String jsonSchema) {
        return """
            %s
            ========================================
            SECTION 1: OUTPUT FORMAT
            ========================================
            %s

            ========================================
            SECTION 2: CONTENT GUIDELINES
            ========================================
            %s

            ========================================
            SECTION 3: JSON RESPONSE SCHEMA
            ========================================
            아래 JSON Schema를 정확히 준수하여 응답하세요.
            %s
            """.formatted(BASE_INSTRUCTION, strategy.getFormat(), strategy.getGuideLine(), jsonSchema);
    }
}
```

### 3-2. `UserPrompt.java`

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/prompt/quiz/user/UserPrompt.java`

```java
package com.icc.qasker.ai.prompt.quiz.user;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserPrompt {

    public static String generate(List<Integer> referencedPages, int quizCount) {
        return """
            [생성 지시]
            - 첨부된 강의노트의 %s 페이지를 참고하여 정확히 %d개의 문제를 생성하세요.
            """.formatted(referencedPages, quizCount);
    }
}
```

> `SystemPrompt`와 `UserPrompt`는 상태를 갖지 않는다. 주입받을 의존성이 없으므로 `@Service`가 불필요하다.

---

## 4단계: GeminiCacheService 수정 — 시스템 프롬프트를 캐시에 포함

> Step 3에서 만든 `createCache()`에 `QuizPromptStrategy`와 `jsonSchema` 파라미터를 추가한다.
> 시스템 프롬프트(JSON Schema 포함)를 캐시의 `systemInstruction`으로 포함시킨다.

### GeminiCacheService.java (변경 부분)

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/service/GeminiCacheService.java`

```java
public String createCache(String fileUri, QuizPromptStrategy strategy, String jsonSchema) {
    try {
        Content pdfContent = Content.builder()
            .role("user")
            .parts(
                Part.builder()
                    .fileData(FileData.builder()
                        .fileUri(fileUri)
                        .mimeType("application/pdf")
                        .build())
                    .build()
            )
            .build();

        String systemPrompt = SystemPrompt.generate(strategy, jsonSchema);
        Content systemInstruction = Content.builder()
            .parts(
                List.of(
                    Part.builder()
                        .text(systemPrompt)
                        .build()
                )
            )
            .build();

        CachedContentRequest request = CachedContentRequest.builder()
            .model(model)
            .systemInstruction(systemInstruction)
            .contents(List.of(pdfContent))
            .ttl(DEFAULT_TTL)
            .build();

        GoogleGenAiCachedContent cache = cachedContentService.create(request);
        // ...
    }
}
```

### Step 3 → Step 4 변경 사항

| 항목                    | Step 3                        | Step 4                                                     |
|-----------------------|-------------------------------|------------------------------------------------------------|
| **시그니처**              | `createCache(String fileUri)` | `createCache(String fileUri, QuizPromptStrategy strategy, String jsonSchema)` |
| **systemInstruction** | 없음                            | `SystemPrompt.generate(strategy, jsonSchema)` 결과를 포함                        |
| **캐시에 포함되는 내용**       | PDF 컨텐츠만                      | PDF 컨텐츠 + 시스템 프롬프트 + JSON Schema                                          |

---

## 5단계: FacadeService + AIController — Structured Output 통합

> Step 2~4를 하나의 흐름으로 연결하고, Structured Output으로 결과를 반환한다.

### FacadeService.java

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/service/FacadeService.java`

```java
package com.icc.qasker.ai.service;

import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.ai.AIProblem;
import com.icc.qasker.ai.dto.ai.AIProblemSet;
import com.icc.qasker.ai.prompt.quiz.common.QuizPromptStrategy;
import com.icc.qasker.ai.prompt.quiz.user.UserPrompt;
import java.util.List;
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
public class FacadeService {

    private final GeminiFileService geminiFileService;
    private final GeminiCacheService geminiCacheService;
    private final ChatModel chatModel;

    /**
     * PDF 업로드 → 캐시 생성(시스템 프롬프트 포함) → 유저 프롬프트 전송 → Structured Output으로 퀴즈 생성.
     */
    public AIProblemSet generateQuiz(String fileUrl, QuizPromptStrategy strategy,
        int quizCount, List<Integer> pageNumbers) {

        FileMetadata metadata = geminiFileService.uploadPdf(fileUrl);
        log.info("업로드 완료: name={}, uri={}", metadata.name(), metadata.uri());

        // ──── 0. JSON Schema 생성 (Structured Output) ────
        BeanOutputConverter<AIProblemSet> converter =
            new BeanOutputConverter<>(AIProblemSet.class);
        String jsonSchema = converter.getFormat();

        String cacheName = null;

        try {
            // ──── 1. 캐시 생성 + 시스템 프롬프트 + JSON Schema 포함 (Step 3 + 4) ────
            cacheName = geminiCacheService.createCache(metadata.uri(), strategy, jsonSchema);
            log.info("캐시 생성 완료: cacheName={}", cacheName);

            // ──── 2. 유저 프롬프트 조립 (Step 4) ────
            String userPrompt = UserPrompt.generate(pageNumbers, quizCount);

            // ──── 3. ChatModel 호출 (캐시 참조 + JSON 응답 강제) ────
            ChatResponse response = chatModel.call(
                new Prompt(userPrompt,
                    GoogleGenAiChatOptions.builder()
                        .useCachedContent(true)
                        .cachedContentName(cacheName)
                        .responseMimeType("application/json")
                        .build())
            );

            String jsonText = response.getResult().getOutput().getText();
            log.info("Gemini 응답 수신 (길이: {}자)", jsonText.length());

            // ──── 4. JSON → AIProblemSet 역직렬화 (Structured Output) ────
            AIProblemSet problemSet = converter.convert(jsonText);

            // ──── 5. 결과 로깅 ────
            for (AIProblem problem : problemSet.quiz()) {
                log.info("문제 {}: {} (선택지 {}개)",
                    problem.number(), problem.title(),
                    problem.selections().size());
            }

            return problemSet;
        } finally {
            geminiCacheService.deleteCache(cacheName);
            geminiFileService.deleteFile(metadata.name());
        }
    }
}

```

### 흐름 상세

```
generateQuiz(fileUrl, QuizType.MULTIPLE, 3, [1,2,3])
  │
  ├── 0. new BeanOutputConverter<>(AIProblemSet.class)
  │      → converter.getFormat() → JSON Schema 문자열
  │
  ├── 1. geminiFileService.uploadPdf(fileUrl)
  │      → FileMetadata { name, uri }
  │
  ├── 2. geminiCacheService.createCache(uri, QuizType.MULTIPLE, jsonSchema)
  │      → 내부: SystemPrompt.generate(strategy, jsonSchema)
  │      → PDF + 시스템 프롬프트 + JSON Schema가 캐시에 포함됨
  │      → cacheName 반환
  │
  ├── 3. UserPrompt.generate([1,2,3], 3)
  │      → "[생성 지시] ...정확히 3개..."
  │
  ├── 4. chatModel.call(userPrompt, options)
  │      → 캐시 참조 + responseMimeType("application/json")
  │      → Gemini가 JSON Schema에 맞는 JSON 문자열 반환
  │
  ├── 5. converter.convert(jsonText)
  │      → JSON → AIProblemSet 객체
  │
  └── finally: 캐시 삭제 + 파일 삭제
```

### AIController.java

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/controller/AIController.java`

```java
package com.icc.qasker.ai.controller;

import com.icc.qasker.ai.dto.ChatRequest;
import com.icc.qasker.ai.dto.MyChatResponse;
import com.icc.qasker.ai.dto.ai.AIProblemSet;
import com.icc.qasker.ai.prompt.quiz.common.QuizType;
import com.icc.qasker.ai.service.ChatService;
import com.icc.qasker.ai.service.FacadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
        @RequestParam(defaultValue = "MULTIPLE") QuizType quizType,
        @RequestParam(defaultValue = "3") int quizCount,
        @RequestParam List<Integer> pageNumbers
    ) {
        return ResponseEntity.ok(
            facadeService.generateQuiz(fileUrl, quizType, quizCount, pageNumbers)
        );
    }
}
```

> `QuizType`은 enum이므로 Spring이 `@RequestParam`의 문자열을 `QuizType.valueOf()`로 자동 변환한다.

### 에러가 발생할 수 있는 지점

```
chatModel.call(...)
  │
  ├── 성공 → JSON 문자열
  │     ├── converter.convert() 성공 → AIProblemSet
  │     └── converter.convert() 실패 → OutputConversionException
  │
  ├── 응답이 빈 텍스트 (null 또는 "")
  │
  └── API 에러
      ├── 429 RESOURCE_EXHAUSTED → TransientAiException
      ├── 400 INVALID_ARGUMENT  → NonTransientAiException
      └── 500 INTERNAL          → TransientAiException
```

> 에러 핸들링은 Step 6에서 체계적으로 구현한다.

---

## 6단계: 검증

### 6-1. 컴파일 확인

```bash
./gradlew :ai:compileJava
```

### 6-2. 서버 시작

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 6-3. 테스트 호출

```bash
# 객관식 3문제
curl -X POST "http://localhost:8080/ai/test-quiz?\
fileUrl=https://files.q-asker.com/실제파일.pdf&\
quizType=MULTIPLE&\
quizCount=3&\
pageNumbers=1,2,3"

# 빈칸 2문제
curl -X POST "http://localhost:8080/ai/test-quiz?\
fileUrl=https://files.q-asker.com/실제파일.pdf&\
quizType=BLANK&\
quizCount=2&\
pageNumbers=5,6,7"

# OX 4문제
curl -X POST "http://localhost:8080/ai/test-quiz?\
fileUrl=https://files.q-asker.com/실제파일.pdf&\
quizType=OX&\
quizCount=4&\
pageNumbers=1,2,3,4,5"
```

### 기대 로그

```
INFO  GeminiFileService   - PDF 업로드 완료: name=files/r1b5ugz, state=PROCESSING
INFO  GeminiFileService   - 파일 처리 완료: name=files/r1b5ugz, uri=https://...
INFO  GeminiCacheService  - 캐시 생성 완료: name=cachedContents/abc123, ...
INFO  FacadeService       - Gemini 응답 수신 (길이: 2340자)
INFO  FacadeService       - 문제 1: TCP 프로토콜은... (선택지 4개)
INFO  FacadeService       - 문제 2: UDP는... (선택지 4개)
INFO  FacadeService       - 문제 3: ... (선택지 4개)
INFO  GeminiCacheService  - 캐시 삭제 완료: name=cachedContents/abc123
INFO  GeminiFileService   - Gemini 파일 삭제 완료: name=files/r1b5ugz
```

### 검증 체크리스트

| 항목        | 확인 포인트                                    |
|-----------|-------------------------------------------|
| **응답 형식** | JSON으로 파싱 가능한가? (에러 없이 AIProblemSet으로 변환) |
| **문제 수**  | 요청한 quizCount와 실제 생성된 문제 수가 일치하는가?        |
| **선택지 수** | MULTIPLE/BLANK → 4개, OX → 2개인가?           |
| **정답 수**  | 각 문제당 correct=true인 선택지가 정확히 1개인가?        |
| **한국어**   | 모든 텍스트가 한국어로 작성되었는가?                      |
| **빈칸 타입** | BLANK일 때 title에 "_______"이 포함되는가?         |
| **OX 타입** | OX일 때 선택지가 "O"와 "X"인가?                    |

---

## Gemini HTTP 메시지

### cachedContents 생성 (시스템 프롬프트 + JSON Schema 포함)

```http
POST /v1beta/cachedContents?key={API_KEY} HTTP/1.1

{
  "model": "models/gemini-2.0-flash",
  "systemInstruction": {
    "parts": [{
      "text": "당신은 대학 강의노트로부터...\n========\nSECTION 1: OUTPUT FORMAT\n...\n========\nSECTION 2: CONTENT GUIDELINES\n...\n========\nSECTION 3: JSON RESPONSE SCHEMA\n아래 JSON Schema를 정확히 준수하여 응답하세요.\n{\"$schema\":...}"
    }]
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

### generateContent (유저 프롬프트만 전송)

```http
POST /v1beta/models/gemini-2.0-flash:generateContent?key={API_KEY} HTTP/1.1

{
  "cachedContent": "cachedContents/abc123def456",
  "contents": [{
    "role": "user",
    "parts": [{
      "text": "[생성 지시]\n- 첨부된 강의노트의 [1, 2, 3] 페이지를 참고하여 정확히 3개의 문제를 생성하세요."
    }]
  }],
  "generationConfig": {
    "responseMimeType": "application/json"
  }
}
```

> 시스템 프롬프트는 캐시에 포함되어 있으므로 `contents`에는 유저 프롬프트만 전송한다.

### HTTP ↔ Java 매핑

| HTTP / Gemini                            | Java (Spring AI)                                                                                       |
|------------------------------------------|--------------------------------------------------------------------------------------------------------|
| `cachedContents.systemInstruction`       | `CachedContentRequest.builder().systemInstruction(...)` + `SystemPrompt.generate(strategy, jsonSchema)` |
| systemInstruction 내 JSON Schema          | `BeanOutputConverter.getFormat()` → `SystemPrompt`의 SECTION 3에 포함                                     |
| `contents[0].parts[0].text`              | `UserPrompt.generate(pageNumbers, quizCount)`                                                          |
| `generationConfig.responseMimeType`      | `GoogleGenAiChatOptions.builder().responseMimeType("application/json")`                                 |
| 응답 `candidates[0].content.parts[0].text` | `response.getResult().getOutput().getText()`                                                           |
| JSON → Java 객체                           | `BeanOutputConverter.convert(jsonText)`                                                                |

---

## BeanOutputConverter 상세

```
new BeanOutputConverter<>(AIProblemSet.class)

  ① 생성자: AIProblemSet.class → JSON Schema 자동 생성
     @JsonPropertyDescription → Schema의 description으로

  ② getFormat(): JSON Schema 포함 문자열 반환
     → SystemPrompt의 SECTION 3에 포함하여 LLM이 정확한 키/구조로 응답하도록 강제
     → responseMimeType("application/json")과 결합하여 이중 보장:
       · API 레벨: JSON 형식 강제 (responseMimeType)
       · 프롬프트 레벨: JSON 구조 강제 (getFormat() → Schema)

  ③ convert(jsonText): JSON → AIProblemSet 역직렬화
     → 실패 시 OutputConversionException
```

### JSON Schema가 프롬프트에 포함되는 흐름

```
BeanOutputConverter<AIProblemSet> converter = new BeanOutputConverter<>(AIProblemSet.class);

converter.getFormat()
  → JSON Schema 문자열 (키 이름, 타입, description 포함)
  → FacadeService에서 createCache()에 전달
  → GeminiCacheService에서 SystemPrompt.generate()에 전달
  → SystemPrompt의 SECTION 3: JSON RESPONSE SCHEMA로 포함
  → 캐시의 systemInstruction에 저장

converter.convert(jsonText)
  → Gemini 응답 JSON을 AIProblemSet으로 역직렬화
  → 같은 converter 인스턴스로 getFormat()과 convert()를 모두 수행
```

> record는 Jackson이 기본 지원하는 불변 DTO이다. Jackson 2.12+는 record의 canonical constructor를 자동 인식하므로
`@JsonCreator`나 `@JsonProperty` 없이도 역직렬화가 동작한다.

---

## 이후 Step 프리뷰 (Step 5: 병렬 배치 생성)

```
BeanOutputConverter<AIProblemSet> converter               ← Step 4
    = new BeanOutputConverter<>(AIProblemSet.class);
String jsonSchema = converter.getFormat();

GeminiFileService.uploadPdf(pdfUrl)                       ← Step 2
  → FileMetadata { name, uri }

GeminiCacheService.createCache(uri, QuizType.MULTIPLE, jsonSchema)  ← Step 3 + 4
  → cacheName (시스템 프롬프트 + JSON Schema + PDF가 캐시에 포함됨)

ChunkSplitter.createPageChunks(...)                       ← Step 5
  → List<ChunkInfo> (각 청크: quizCount + referencedPages)

[병렬] 각 청크마다:                                        ← Step 4 + 5
  String userPrompt = UserPrompt.generate(
      chunk.referencedPages, chunk.quizCount
  );  // 유저 프롬프트만 청크별로 다름

  ChatResponse response = chatModel.call(
      new Prompt(userPrompt, options { cacheName, "application/json" })
  );

  AIProblemSet result = converter.convert(
      response.getResult().getOutput().getText());

finally:
  geminiCacheService.deleteCache(cacheName)
  geminiFileService.deleteFile(fileName)
```

---

## 참고 링크

- [Spring AI — Structured Output Converter](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
- [Spring AI — BeanOutputConverter Javadoc](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/converter/BeanOutputConverter.html)
- [Gemini API — Structured Output (JSON)](https://ai.google.dev/gemini-api/docs/structured-output)
- [Gemini API — Context Caching](https://ai.google.dev/gemini-api/docs/caching)
- [Jackson — Record Support](https://github.com/FasterXML/jackson-databind#jdk-record-support)
- [Spring AI — Google GenAI Chat Options](https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html)
- Python 프롬프트 원본: `ai/app/prompt/core/multiple.py`, `blank.py`, `ox.py`
