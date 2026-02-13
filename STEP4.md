# Step 4: 프롬프트 시스템 + Structured Output

> Python 서버의 프롬프트 템플릿과 JSON 구조 강제를 Java로 포팅한다.
> LLM 응답을 `AIProblemSet` Java 객체로 자동 역직렬화하는 Structured Output 파이프라인을 구축한다.

---

## 전체 플로우

```
cacheName (Step 3에서 획득)
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│  Step 4에서 구현하는 영역                                       │
│                                                              │
│  1. PromptBuilder.build(quizType, difficulty, count, pages)  │
│     └ QuizPromptTemplates에서 타입별 프롬프트 조립              │
│     └ BeanOutputConverter.getFormat()으로 JSON 스키마 추가     │
│                                                              │
│  2. chatModel.call(Prompt, GoogleGenAiChatOptions {           │
│         cachedContentName,                                   │
│         useCachedContent: true,                              │
│         responseMimeType: "application/json"                 │
│     })                                                       │
│                                                              │
│  3. BeanOutputConverter.convert(responseText)                │
│     └ JSON 문자열 → AIProblemSet 자동 역직렬화                 │
└──────────────────────────────────────────────────────────────┘
       │
       ▼
AIProblemSet { quiz: [AIProblem, AIProblem, ...] }
→ 이후 Step 5에서 병렬 배치 생성 시 사용
```

---

## 완성 후 디렉토리 구조

```
modules/ai/src/main/java/com/icc/qasker/ai/
├── config/
│   ├── GeminiCacheConfig.java          (기존 — Step 3)
│   └── GeminiFileRestClientConfig.java (기존 — Step 2)
├── controller/
│   └── AIController.java               (기존 — 테스트용 엔드포인트 추가)
├── dto/
│   ├── AIProblemSet.java               ← NEW (Structured Output 루트 DTO)
│   ├── AIProblem.java                  ← NEW (개별 문제 DTO)
│   ├── AISelection.java               ← NEW (선택지 DTO)
│   ├── ChatRequest.java               (기존)
│   ├── GeminiFileUploadResponse.java   (기존 — Step 2)
│   └── MyChatResponse.java            (기존)
├── service/
│   ├── ChatService.java               (기존)
│   ├── FacadeService.java             (기존 — Step 4 테스트용으로 수정)
│   ├── GeminiCacheService.java        (기존 — Step 3)
│   └── GeminiFileService.java         (기존 — Step 2)
└── util/
    ├── PdfUtils.java                  (기존 — Step 2)
    ├── PromptBuilder.java             ← NEW (프롬프트 조립기)
    └── QuizPromptTemplates.java       ← NEW (타입별 프롬프트 상수)
```

---

## 배경: Structured Output이란?

### 문제 상황

LLM에게 "객관식 문제 5개 만들어줘"라고 하면 응답이 매번 다른 형식으로 온다:

```
// 어떤 때는 이렇게
1. 다음 중 올바른 것은? ...

// 어떤 때는 이렇게
문제1: ...
A) ...

// 또 어떤 때는 JSON이지만 키 이름이 다르게
{ "questions": [{ "q": "...", "a": "..." }] }
```

**Java 코드에서 이걸 파싱하는 것은 불가능하다.** 응답 형식이 보장되지 않기 때문이다.

### 해결: Structured Output

**Gemini API**와 **Spring AI**를 결합하면 응답 형식을 **강제**할 수 있다:

```
┌────────────────────────────────────────────────────────────────┐
│                    Structured Output 파이프라인                  │
│                                                                │
│  1. Java 클래스 정의                                            │
│     AIProblemSet { quiz: List<AIProblem> }                     │
│                                                                │
│  2. BeanOutputConverter가 JSON Schema 자동 생성                 │
│     → 프롬프트에 "이 스키마대로 응답해라" 추가                     │
│                                                                │
│  3. GoogleGenAiChatOptions.responseMimeType("application/json")│
│     → Gemini API 레벨에서 JSON 응답 강제                         │
│                                                                │
│  4. BeanOutputConverter.convert(jsonText)                      │
│     → JSON 문자열 → AIProblemSet 객체 자동 변환                  │
└────────────────────────────────────────────────────────────────┘
```

이중 안전장치:
- **프롬프트 레벨**: JSON Schema를 프롬프트에 포함 → LLM이 구조를 인지
- **API 레벨**: `responseMimeType: "application/json"` → Gemini가 JSON만 반환하도록 강제

### Python 서버와의 차이

> Python 서버는 OpenAI의 `response_format: { type: "json_schema", json_schema: {...} }`로 구조를 강제했다.
> Spring AI에서는 `BeanOutputConverter` + `responseMimeType`으로 동일한 효과를 달성한다.
> **Java 클래스를 정의하면 JSON Schema가 자동 생성**되므로, 수동으로 스키마를 작성할 필요가 없다.

---

## Spring AI의 BeanOutputConverter 구조

### 핵심 역할

```
┌──────────────────────────────────────────────────────────────────┐
│  BeanOutputConverter<AIProblemSet>                               │
│                                                                  │
│  입력: AIProblemSet.class (Java 클래스)                           │
│                                                                  │
│  제공하는 기능:                                                    │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ getFormat()                                                │  │
│  │ → "Your response should be in JSON format..."              │  │
│  │ → JSON Schema 포함 문자열 반환                               │  │
│  │ → 이걸 프롬프트에 추가하면 LLM이 구조를 인지                   │  │
│  └────────────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ convert(String jsonText)                                   │  │
│  │ → JSON 문자열을 AIProblemSet 객체로 역직렬화                  │  │
│  │ → Jackson ObjectMapper 내부 사용                            │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

### getFormat()이 반환하는 문자열 예시

```
Your response should be in JSON format.
Do not include any explanations, only provide a RFC8259 compliant JSON response
following this format without deviation.
Do not include markdown code blocks in your response.
Here is the JSON Schema instance your output must adhere to:
```json
{
  "type": "object",
  "properties": {
    "quiz": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "number": { "type": "integer", "description": "문제 번호 (1부터 시작)" },
          "title": { "type": "string", "description": "문제 본문" },
          "selections": { ... },
          "explanation": { "type": "string", "description": "정답 해설" },
          "referencedPages": { ... }
        }
      }
    }
  }
}
```​
```

> `@JsonPropertyDescription` 어노테이션에 작성한 설명이 JSON Schema의 `description`으로 들어간다.
> 이 description이 LLM에게 각 필드의 의미를 알려주는 역할을 한다.

### 전체 흐름 요약

```
AIProblemSet.class
       │
       ▼ (1) new BeanOutputConverter<>(AIProblemSet.class)
BeanOutputConverter<AIProblemSet> converter
       │
       ├──▶ (2) converter.getFormat()
       │         → JSON Schema 문자열 → 프롬프트에 추가
       │
       ▼ (3) chatModel.call(prompt + schema, options { responseMimeType: "application/json" })
  Gemini 응답: "{ \"quiz\": [{ \"number\": 1, ... }] }"
       │
       ▼ (4) converter.convert(responseText)
  AIProblemSet { quiz: [AIProblem { number=1, title="...", ... }] }
```

---

## 1단계: DTO 생성 — AIProblemSet / AIProblem / AISelection

> BeanOutputConverter가 JSON Schema를 생성할 수 있도록, Gemini 응답 구조를 Java 클래스로 정의한다.

### 기존 DTO와의 관계

```
┌──────────────────────────────────────────────────────────────┐
│  ai 모듈 (Step 4에서 생성)              quiz-api 모듈 (기존)    │
│                                                              │
│  AIProblemSet                          ProblemSetGeneratedEvent │
│    └ quiz: List<AIProblem>               └ quiz: List<QuizGeneratedFromAI> │
│        ├ number                              ├ number        │
│        ├ title                               ├ title         │
│        ├ selections: List<AISelection>       ├ selections: List<SelectionsOfAI> │
│        │   ├ content                         │   ├ content   │
│        │   └ correct                         │   └ correct   │
│        ├ explanation                         ├ explanation   │
│        └ referencedPages                     └ referencedPages │
│                                                              │
│  → 구조가 거의 동일. Step 7에서 단순 필드 복사 매핑.             │
└──────────────────────────────────────────────────────────────┘
```

> **왜 ai 모듈에 별도 DTO를 만드는가?**
> ai 모듈은 `quiz-api`에 의존하지 않는다. 모듈 간 결합도를 낮추기 위해 독립 DTO를 사용한다.
> Step 7에서 quiz-impl이 `ai` 모듈을 의존하며, 매퍼로 변환한다.

### 1-1. `AISelection.java`

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/dto/AISelection.java`

```java
package com.icc.qasker.ai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 퀴즈 선택지 하나를 나타내는 DTO.
 * BeanOutputConverter가 이 클래스의 필드와 description을 JSON Schema로 변환한다.
 *
 * @param content 선택지 텍스트 (예: "TCP는 비연결형 프로토콜이다")
 * @param correct 정답 여부 (정답이면 true)
 */
public record AISelection(

    @JsonPropertyDescription("선택지 텍스트")
    String content,

    @JsonPropertyDescription("정답 여부 (정답이면 true, 오답이면 false)")
    boolean correct
) {

}
```

### 1-2. `AIProblem.java`

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/dto/AIProblem.java`

```java
package com.icc.qasker.ai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * AI가 생성한 퀴즈 문제 하나를 나타내는 DTO.
 *
 * <pre>
 * 예시 JSON:
 * {
 *   "number": 1,
 *   "title": "다음 중 TCP 프로토콜의 특징으로 올바른 것은?",
 *   "selections": [
 *     { "content": "연결 지향형이다", "correct": true },
 *     { "content": "비연결형이다", "correct": false },
 *     ...
 *   ],
 *   "explanation": "TCP는 3-way handshake를 통해 연결을 수립하는 연결 지향형 프로토콜이다.",
 *   "referencedPages": [3, 4]
 * }
 * </pre>
 *
 * @param number         문제 번호 (1부터 시작, 순차 증가)
 * @param title          문제 본문
 * @param selections     선택지 목록
 * @param explanation    정답 해설
 * @param referencedPages 이 문제가 참조하는 PDF 페이지 번호 목록
 */
public record AIProblem(

    @JsonPropertyDescription("문제 번호 (1부터 시작)")
    int number,

    @JsonPropertyDescription("문제 본문")
    String title,

    @JsonPropertyDescription("선택지 목록")
    List<AISelection> selections,

    @JsonPropertyDescription("정답 해설")
    String explanation,

    @JsonPropertyDescription("이 문제가 참조하는 PDF 페이지 번호 목록")
    List<Integer> referencedPages
) {

}
```

### 1-3. `AIProblemSet.java`

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/dto/AIProblemSet.java`

```java
package com.icc.qasker.ai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * AI가 생성한 퀴즈 문제 세트. BeanOutputConverter의 루트 타입.
 *
 * <pre>
 * Gemini 응답 JSON:
 * {
 *   "quiz": [
 *     { "number": 1, "title": "...", "selections": [...], "explanation": "...", "referencedPages": [...] },
 *     { "number": 2, ... }
 *   ]
 * }
 * </pre>
 *
 * @param quiz 생성된 문제 목록
 */
public record AIProblemSet(

    @JsonPropertyDescription("생성된 퀴즈 문제 목록")
    List<AIProblem> quiz
) {

}
```

### 포인트 정리

| 주제 | 설명 |
|---|---|
| **record 사용** | 불변 DTO + 자동 생성자/getter. Jackson이 record 역직렬화를 기본 지원 |
| **`@JsonPropertyDescription`** | BeanOutputConverter가 JSON Schema의 `description`으로 변환. LLM에게 필드 의미 전달 |
| **`referencedPages`** | Python의 `ProblemDTO.referencedPages`에 대응. 문제가 참조하는 PDF 페이지 번호 |
| **quiz-api와 독립** | ai 모듈은 quiz-api에 의존하지 않음. 필드 구조는 동일하되 패키지 분리 |

### BeanOutputConverter가 생성하는 JSON Schema (자동)

위 DTO를 기반으로 `new BeanOutputConverter<>(AIProblemSet.class)`가 아래와 같은 스키마를 자동 생성한다:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "quiz": {
      "type": "array",
      "description": "생성된 퀴즈 문제 목록",
      "items": {
        "type": "object",
        "properties": {
          "number": {
            "type": "integer",
            "description": "문제 번호 (1부터 시작)"
          },
          "title": {
            "type": "string",
            "description": "문제 본문"
          },
          "selections": {
            "type": "array",
            "description": "선택지 목록",
            "items": {
              "type": "object",
              "properties": {
                "content": {
                  "type": "string",
                  "description": "선택지 텍스트"
                },
                "correct": {
                  "type": "boolean",
                  "description": "정답 여부 (정답이면 true, 오답이면 false)"
                }
              }
            }
          },
          "explanation": {
            "type": "string",
            "description": "정답 해설"
          },
          "referencedPages": {
            "type": "array",
            "description": "이 문제가 참조하는 PDF 페이지 번호 목록",
            "items": {
              "type": "integer"
            }
          }
        }
      }
    }
  }
}
```

> **직접 작성할 필요 없다.** Java 클래스의 필드 타입과 `@JsonPropertyDescription`만으로 자동 생성된다.

---

## 2단계: 프롬프트 템플릿 — `QuizPromptTemplates.java`

> Python 서버의 `prompt/core/multiple.py`, `blank.py`, `ox.py`에 대응하는 Java 상수 클래스.

### Python 프롬프트 구조 (포팅 대상)

```
Python 서버의 프롬프트 조립 구조:

system_message = """
    당신은 대학 강의노트로부터 평가용 퀴즈를 생성하는 AI입니다.
    ... 정확히 {chunk.quiz_count}개 생성하세요.
    작성 규칙: 한국어, 개행 가독성, 강의노트 참조 금지
    문제 생성 지침: {prompt_factory.get_quiz_generation_guide(dok_level, quiz_type)}
    문항 형식: {prompt_factory.get_quiz_format(quiz_type)}
"""
```

이를 Java에서는 두 클래스로 분리한다:

| 역할 | Python | Java |
|---|---|---|
| 타입별 생성 지침 + 형식 상수 | `prompt/core/multiple.py`, `blank.py`, `ox.py` | `QuizPromptTemplates.java` |
| 전체 프롬프트 조립 | `prompt/prompt_factory.py` + `generate_service.py` | `PromptBuilder.java` |

### 파일 생성

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/util/QuizPromptTemplates.java`

```java
package com.icc.qasker.ai.util;

/**
 * 퀴즈 타입별/난이도별 프롬프트 상수.
 *
 * <p>Python 서버의 {@code prompt/core/multiple.py}, {@code blank.py}, {@code ox.py}와
 * {@code prompt/prompt_factory.py}를 Java 상수로 포팅한 것이다.</p>
 *
 * <p>각 상수는 {@link PromptBuilder}에서 조립 시 참조한다.</p>
 */
public final class QuizPromptTemplates {

    private QuizPromptTemplates() {
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 퀴즈 타입별 생성 지침 (quiz generation guide)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 객관식 (MULTIPLE) 문제 생성 지침.
     * Python: prompt/core/multiple.py — get_quiz_generation_guide()
     */
    public static final String MULTIPLE_GUIDE = """
        [객관식 문제 생성 지침]
        - 하나의 문제는 [전제]와 [질문]으로 구성한다.
        - [전제]: 문제 상황이나 조건을 서술하는 1~3문장. 강의노트의 핵심 개념을 활용한다.
        - [질문]: "다음 중 올바른 것은?", "다음 중 올바르지 않은 것은?" 등의 질문문.
        - [전제]와 [질문]을 합쳐서 title 필드에 넣는다.
        - 선택지는 정확히 4개를 생성한다. 정답 1개, 오답 3개.
        - 오답은 그럴듯하지만 명확히 틀린 내용이어야 한다.
        - 오답이 모호하거나 "~일 수도 있다" 식의 애매한 표현은 금지한다.
        - 정답의 correct 필드를 true로, 오답은 false로 설정한다.
        """;

    /**
     * 빈칸 (BLANK) 문제 생성 지침.
     * Python: prompt/core/blank.py — get_quiz_generation_guide()
     */
    public static final String BLANK_GUIDE = """
        [빈칸 문제 생성 지침]
        - 강의노트의 핵심 개념을 포함하는 완전한 진술문을 작성한다.
        - 진술문에서 핵심 용어/개념 하나를 "_______"(밑줄 7개)로 대체한다.
        - 밑줄로 대체된 부분이 정답이 된다.
        - title 필드에 밑줄이 포함된 진술문을 넣는다.
          예: "TCP는 _______를 통해 연결을 수립하는 연결 지향형 프로토콜이다."
        - 선택지는 정확히 4개를 생성한다. 정답 1개, 오답 3개.
        - 정답: 밑줄에 들어갈 올바른 용어/개념.
        - 오답: 같은 카테고리의 다른 용어로, 그럴듯하지만 명확히 틀린 것.
        - 정답의 correct 필드를 true로, 오답은 false로 설정한다.
        """;

    /**
     * OX (참/거짓) 문제 생성 지침.
     * Python: prompt/core/ox.py — get_quiz_generation_guide()
     */
    public static final String OX_GUIDE = """
        [OX 문제 생성 지침]
        - 강의노트의 내용을 바탕으로 참 또는 거짓인 단일 진술문을 작성한다.
        - title 필드에 진술문을 넣는다.
          예: "TCP는 비연결형 프로토콜이다."
        - 선택지는 정확히 2개를 생성한다.
          - { "content": "O", "correct": true/false }
          - { "content": "X", "correct": true/false }
        - 진술문이 참이면 O가 correct=true, 거짓이면 X가 correct=true.
        - 참/거짓 비율은 대략 절반씩 되도록 한다.
        - 진술문은 명확해야 한다. "~일 수도 있다" 같은 모호한 표현은 금지한다.
        """;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 퀴즈 타입별 출력 형식 (quiz format)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 객관식 (MULTIPLE) 출력 형식 설명.
     * Python: prompt/core/multiple.py — get_quiz_format()
     */
    public static final String MULTIPLE_FORMAT = """
        [객관식 문항 형식]
        - number: 문제 번호 (1부터 순차 증가)
        - title: [전제] + [질문]을 합친 문제 본문
        - selections: 4개의 선택지 배열
          - content: 선택지 텍스트
          - correct: 정답이면 true, 오답이면 false (정답은 반드시 1개)
        - explanation: 정답인 이유를 2~3문장으로 설명
        - referencedPages: 이 문제의 근거가 되는 PDF 페이지 번호 목록
        """;

    /**
     * 빈칸 (BLANK) 출력 형식 설명.
     * Python: prompt/core/blank.py — get_quiz_format()
     */
    public static final String BLANK_FORMAT = """
        [빈칸 문항 형식]
        - number: 문제 번호 (1부터 순차 증가)
        - title: "_______"(밑줄 7개)이 포함된 진술문
        - selections: 4개의 선택지 배열
          - content: 선택지 텍스트 (빈칸에 들어갈 후보)
          - correct: 정답이면 true, 오답이면 false (정답은 반드시 1개)
        - explanation: 정답인 이유를 2~3문장으로 설명
        - referencedPages: 이 문제의 근거가 되는 PDF 페이지 번호 목록
        """;

    /**
     * OX (참/거짓) 출력 형식 설명.
     * Python: prompt/core/ox.py — get_quiz_format()
     */
    public static final String OX_FORMAT = """
        [OX 문항 형식]
        - number: 문제 번호 (1부터 순차 증가)
        - title: 참 또는 거짓인 진술문
        - selections: 2개의 선택지 배열
          - { "content": "O", "correct": true 또는 false }
          - { "content": "X", "correct": true 또는 false }
        - explanation: 해당 진술이 참/거짓인 이유를 2~3문장으로 설명
        - referencedPages: 이 문제의 근거가 되는 PDF 페이지 번호 목록
        """;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 난이도별 생성 지침 (difficulty guide)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * RECALL (기억) 난이도 — DOK Level 1.
     * 단순 사실/개념의 기억 여부를 확인하는 문제.
     */
    public static final String RECALL_GUIDE = """
        [난이도: 기억 (Recall)]
        - 강의노트에 명시적으로 서술된 사실, 정의, 용어를 그대로 확인하는 문제를 생성한다.
        - 추론이나 적용은 요구하지 않는다.
        - "~이란?", "~의 정의는?", "~에 해당하는 것은?" 형태의 직접적 질문을 사용한다.
        """;

    /**
     * SKILLS (이해/적용) 난이도 — DOK Level 2.
     * 개념을 이해하고 새로운 상황에 적용하는 능력을 확인하는 문제.
     */
    public static final String SKILLS_GUIDE = """
        [난이도: 이해/적용 (Skills)]
        - 개념의 이해와 적용 능력을 확인하는 문제를 생성한다.
        - 단순 암기가 아니라, 개념을 새로운 상황에 적용하거나 예시를 판별하는 능력을 평가한다.
        - "~한 상황에서 올바른 것은?", "~를 적용하면?" 형태의 질문을 사용한다.
        - 개념 간 비교, 원인-결과 관계 파악을 포함할 수 있다.
        """;

    /**
     * STRATEGIC (분석/평가) 난이도 — DOK Level 3.
     * 분석, 비교, 평가, 종합적 판단 능력을 확인하는 문제.
     */
    public static final String STRATEGIC_GUIDE = """
        [난이도: 분석/평가 (Strategic)]
        - 분석, 비교, 평가, 종합적 판단 능력을 확인하는 문제를 생성한다.
        - 여러 개념을 종합하거나, 장단점을 비교하거나, 상황을 분석하는 문제를 출제한다.
        - "~의 장점과 단점을 고려했을 때", "~와 ~를 비교하면" 형태의 복합 질문을 사용한다.
        - 단순 사실 확인이 아니라 사고력을 요구해야 한다.
        """;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 조회 메서드
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 퀴즈 타입에 해당하는 생성 지침을 반환한다.
     * Python: prompt_factory.get_quiz_generation_guide(quiz_type)
     *
     * @param quizType "MULTIPLE", "BLANK", "OX"
     * @return 생성 지침 문자열
     */
    public static String getGuide(String quizType) {
        return switch (quizType) {
            case "MULTIPLE" -> MULTIPLE_GUIDE;
            case "BLANK" -> BLANK_GUIDE;
            case "OX" -> OX_GUIDE;
            default -> throw new IllegalArgumentException("지원하지 않는 퀴즈 타입: " + quizType);
        };
    }

    /**
     * 퀴즈 타입에 해당하는 출력 형식 설명을 반환한다.
     * Python: prompt_factory.get_quiz_format(quiz_type)
     *
     * @param quizType "MULTIPLE", "BLANK", "OX"
     * @return 출력 형식 문자열
     */
    public static String getFormat(String quizType) {
        return switch (quizType) {
            case "MULTIPLE" -> MULTIPLE_FORMAT;
            case "BLANK" -> BLANK_FORMAT;
            case "OX" -> OX_FORMAT;
            default -> throw new IllegalArgumentException("지원하지 않는 퀴즈 타입: " + quizType);
        };
    }

    /**
     * 난이도에 해당하는 생성 지침을 반환한다.
     * Python: prompt_factory.get_quiz_generation_guide(dok_level)
     *
     * @param difficultyType "RECALL", "SKILLS", "STRATEGIC"
     * @return 난이도 지침 문자열
     */
    public static String getDifficultyGuide(String difficultyType) {
        return switch (difficultyType) {
            case "RECALL" -> RECALL_GUIDE;
            case "SKILLS" -> SKILLS_GUIDE;
            case "STRATEGIC" -> STRATEGIC_GUIDE;
            default -> throw new IllegalArgumentException("지원하지 않는 난이도: " + difficultyType);
        };
    }
}
```

### 포인트 정리

| 주제 | 설명 |
|---|---|
| **`final class` + `private` 생성자** | 인스턴스 생성 불가. 상수와 static 메서드만 제공하는 유틸리티 클래스 |
| **String 파라미터** | ai 모듈은 quiz-api에 의존하지 않으므로 `QuizType` enum을 직접 참조 불가. `enum.name()` 문자열을 받는다 |
| **switch expression** | Java 14+ 문법. `→` 화살표로 간결하게 분기. `break` 불필요 |
| **text block (`"""`)** | Java 15+ 문법. 여러 줄 문자열을 가독성 좋게 작성 |
| **Python 대응** | `getGuide()` = `prompt_factory.get_quiz_generation_guide()`, `getFormat()` = `prompt_factory.get_quiz_format()` |

### 왜 String 파라미터인가?

```
┌────────────────────────────────────────────────────────┐
│  모듈 의존성 구조                                        │
│                                                        │
│  quiz-impl ──depends──▶ quiz-api (QuizType enum 정의)  │
│  quiz-impl ──depends──▶ ai      (PromptBuilder 호출)   │  ← Step 7에서 추가
│  ai        ──depends──▶ global  (에러 처리)             │
│                                                        │
│  ai 모듈은 quiz-api를 의존하지 않는다.                    │
│  따라서 QuizType enum을 직접 참조할 수 없다.              │
│                                                        │
│  호출 측 (Step 7):                                      │
│  PromptBuilder.build(                                  │
│      quizType.name(),        // "MULTIPLE"             │
│      difficultyType.name(),  // "RECALL"               │
│      quizCount,                                        │
│      referencedPages                                   │
│  );                                                    │
└────────────────────────────────────────────────────────┘
```

---

## 3단계: 프롬프트 빌더 — `PromptBuilder.java`

> Python 서버의 `prompt_factory.py` + `generate_service.py:50-63`에 대응.
> 타입별 프롬프트 조각들을 하나의 system message로 조립한다.

### Python 원본 프롬프트 구조 (포팅 대상)

```python
# generate_service.py:50-63
system_message = f"""
    당신은 대학 강의노트로부터 평가용 퀴즈를 생성하는 AI입니다.
    첨부된 강의노트의 {chunk.page_numbers} 페이지를 참고하여
    정확히 {chunk.quiz_count}개의 문제를 생성하세요.

    [작성 규칙]
    - 모든 텍스트는 한국어로 작성
    - 개행을 사용하여 가독성 확보
    - "강의노트에 따르면", "교재에 의하면" 같은 출처 언급 금지

    {prompt_factory.get_quiz_generation_guide(dok_level, quiz_type)}
    {prompt_factory.get_quiz_format(quiz_type)}
"""
```

### 파일 생성

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/util/PromptBuilder.java`

```java
package com.icc.qasker.ai.util;

import com.icc.qasker.ai.dto.AIProblemSet;
import java.util.List;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * 퀴즈 생성용 시스템 프롬프트를 조립한다.
 *
 * <p>Python 서버의 {@code prompt_factory.py} + {@code generate_service.py:50-63}에 대응한다.</p>
 *
 * <pre>
 * 프롬프트 구조:
 * ┌────────────────────────────────────────────────────┐
 * │ [기본 역할 + 작성 규칙]                               │  ← BASE_INSTRUCTION
 * │ [생성 개수 + 참조 페이지]                              │  ← 동적 파라미터
 * │ [퀴즈 타입별 생성 지침]                                │  ← QuizPromptTemplates.getGuide()
 * │ [난이도별 생성 지침]                                   │  ← QuizPromptTemplates.getDifficultyGuide()
 * │ [퀴즈 타입별 출력 형식]                                │  ← QuizPromptTemplates.getFormat()
 * │ [JSON 스키마 (BeanOutputConverter)]                  │  ← converter.getFormat()
 * └────────────────────────────────────────────────────┘
 * </pre>
 */
public final class PromptBuilder {

    private PromptBuilder() {
    }

    /**
     * 기본 역할 설명 + 작성 규칙.
     * Python: generate_service.py의 system_message 상단부.
     */
    private static final String BASE_INSTRUCTION = """
        당신은 대학 강의노트로부터 평가용 퀴즈를 생성하는 AI입니다.

        [작성 규칙]
        - 모든 텍스트는 한국어로 작성한다.
        - 개행을 적절히 사용하여 가독성을 확보한다.
        - "강의노트에 따르면", "교재에 의하면" 같은 출처 언급을 하지 않는다.
        - 문제와 해설은 강의노트의 내용을 기반으로 하되, 독립적으로 이해 가능하게 작성한다.
        """;

    /**
     * BeanOutputConverter 인스턴스 (스레드 세이프, 재사용).
     * AIProblemSet 클래스의 필드 구조와 {@code @JsonPropertyDescription}을 분석하여
     * JSON Schema를 자동 생성한다.
     */
    private static final BeanOutputConverter<AIProblemSet> CONVERTER =
        new BeanOutputConverter<>(AIProblemSet.class);

    /**
     * 퀴즈 생성용 시스템 프롬프트를 조립한다.
     *
     * @param quizType        퀴즈 타입 ("MULTIPLE", "BLANK", "OX")
     * @param difficultyType  난이도 ("RECALL", "SKILLS", "STRATEGIC")
     * @param quizCount       생성할 문제 수
     * @param referencedPages 참조할 PDF 페이지 번호 목록
     * @return 완성된 시스템 프롬프트 문자열
     */
    public static String build(String quizType, String difficultyType,
                               int quizCount, List<Integer> referencedPages) {

        return BASE_INSTRUCTION
            + "\n"
            + buildDynamicSection(quizCount, referencedPages)
            + "\n"
            + QuizPromptTemplates.getGuide(quizType)
            + "\n"
            + QuizPromptTemplates.getDifficultyGuide(difficultyType)
            + "\n"
            + QuizPromptTemplates.getFormat(quizType)
            + "\n"
            + CONVERTER.getFormat();
    }

    /**
     * 생성 개수와 참조 페이지를 동적으로 삽입하는 섹션.
     */
    private static String buildDynamicSection(int quizCount, List<Integer> referencedPages) {
        return """
            [생성 지시]
            - 첨부된 강의노트의 %s 페이지를 참고하여 정확히 %d개의 문제를 생성하세요.
            - 각 문제의 referencedPages에는 해당 문제의 근거가 되는 페이지 번호를 포함하세요.
            """.formatted(referencedPages, quizCount);
    }

    /**
     * BeanOutputConverter를 반환한다.
     * ChatModel 응답을 AIProblemSet으로 변환할 때 사용.
     *
     * @return BeanOutputConverter 인스턴스
     */
    public static BeanOutputConverter<AIProblemSet> getConverter() {
        return CONVERTER;
    }
}
```

### 조립 결과 예시

`PromptBuilder.build("MULTIPLE", "RECALL", 3, List.of(1, 2, 3))` 호출 시:

```
당신은 대학 강의노트로부터 평가용 퀴즈를 생성하는 AI입니다.

[작성 규칙]
- 모든 텍스트는 한국어로 작성한다.
- 개행을 적절히 사용하여 가독성을 확보한다.
- "강의노트에 따르면", "교재에 의하면" 같은 출처 언급을 하지 않는다.
- 문제와 해설은 강의노트의 내용을 기반으로 하되, 독립적으로 이해 가능하게 작성한다.

[생성 지시]
- 첨부된 강의노트의 [1, 2, 3] 페이지를 참고하여 정확히 3개의 문제를 생성하세요.
- 각 문제의 referencedPages에는 해당 문제의 근거가 되는 페이지 번호를 포함하세요.

[객관식 문제 생성 지침]
- 하나의 문제는 [전제]와 [질문]으로 구성한다.
...

[난이도: 기억 (Recall)]
- 강의노트에 명시적으로 서술된 사실, 정의, 용어를 그대로 확인하는 문제를 생성한다.
...

[객관식 문항 형식]
- number: 문제 번호 (1부터 순차 증가)
...

Your response should be in JSON format.
Do not include any explanations, only provide a RFC8259 compliant JSON response ...
```json
{ "$schema": "...", "type": "object", ... }
```​
```

### 포인트 정리

| 주제 | 설명 |
|---|---|
| **`final class` + `private` 생성자** | PromptBuilder도 유틸리티 클래스. 상태 없음 |
| **`static final CONVERTER`** | BeanOutputConverter는 불변이므로 인스턴스 하나를 재사용. 스레드 세이프 |
| **`getConverter()` 분리** | 프롬프트 조립과 응답 파싱을 같은 converter로 보장. build()에서 스키마를 넣고, 호출 측에서 convert()로 파싱 |
| **`.formatted()` 사용** | text block과 결합하여 동적 값 삽입. `String.format()`의 인스턴스 메서드 버전 |
| **Python 대응** | `build()` = `generate_service.py:50-63`의 system_message 조립 전체 |

### 왜 static 유틸리티인가?

> PromptBuilder는 상태를 갖지 않는다. 모든 정보(quizType, difficultyType, quizCount, referencedPages)를
> 파라미터로 받아 문자열을 반환할 뿐이다.
> `@Service`로 만들 필요가 없다 — 주입받을 의존성이 없기 때문이다.
>
> ```
> [Spring Bean이 필요한 경우]
> - 다른 Bean을 주입받아야 할 때 (예: GeminiCacheService → cacheService)
> - 설정값(@Value)을 주입받아야 할 때 (예: GeminiCacheService → model)
> - AOP 프록시가 필요할 때 (예: @CircuitBreaker)
>
> [static 유틸리티로 충분한 경우]
> - 순수 함수: 입력 → 출력만 있고 외부 의존성 없음
> - 상태 없음: 모든 정보를 파라미터로 받음
> - PromptBuilder, PdfUtils, ChunkSplitter가 여기에 해당
> ```

---

## 4단계: ChatModel 호출 + Structured Output 통합

> Step 3의 캐시와 Step 4의 프롬프트/Structured Output을 결합한다.

### 전체 호출 코드

```java
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.util.PromptBuilder;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

// ──── 1. 프롬프트 조립 ────
String systemPrompt = PromptBuilder.build(
    "MULTIPLE",             // quizType
    "RECALL",               // difficultyType
    3,                      // quizCount
    List.of(1, 2, 3)        // referencedPages
);

// ──── 2. ChatModel 호출 (캐시 참조 + JSON 응답 강제) ────
ChatResponse response = chatModel.call(
    new Prompt(systemPrompt,
        GoogleGenAiChatOptions.builder()
            .useCachedContent(true)
            .cachedContentName(cacheName)
            .responseMimeType("application/json")
            .build()
    ));

// ──── 3. JSON → AIProblemSet 역직렬화 ────
String jsonText = response.getResult().getOutput().getText();
BeanOutputConverter<AIProblemSet> converter = PromptBuilder.getConverter();
AIProblemSet result = converter.convert(jsonText);

// ──── 4. 결과 사용 ────
for (AIProblem problem : result.quiz()) {
    log.info("문제 {}: {} (선택지 {}개, 참조 페이지: {})",
        problem.number(), problem.title(),
        problem.selections().size(), problem.referencedPages());
}
```

### GoogleGenAiChatOptions 설정 상세

| 옵션 | 값 | 역할 |
|---|---|---|
| `useCachedContent` | `true` | 캐시 사용 활성화 |
| `cachedContentName` | `"cachedContents/abc123"` | Step 3에서 생성한 캐시 참조 |
| `responseMimeType` | `"application/json"` | **Gemini API 레벨에서 JSON 응답 강제** |

> `responseMimeType("application/json")`을 설정하면 Gemini가 **반드시 JSON만 반환**한다.
> "여기 답변입니다:" 같은 부가 텍스트가 포함되지 않는다.
> 이것과 프롬프트의 JSON Schema가 **이중 안전장치** 역할을 한다.

### Gemini 응답 예시

```json
{
  "quiz": [
    {
      "number": 1,
      "title": "TCP 프로토콜에 대한 다음 설명 중 올바른 것은?",
      "selections": [
        { "content": "연결 지향형 프로토콜로, 3-way handshake를 통해 연결을 수립한다.", "correct": true },
        { "content": "비연결형 프로토콜로, 데이터를 독립적으로 전송한다.", "correct": false },
        { "content": "오류 검출 기능이 없어 신뢰성이 낮다.", "correct": false },
        { "content": "UDP보다 전송 속도가 항상 빠르다.", "correct": false }
      ],
      "explanation": "TCP는 3-way handshake(SYN → SYN-ACK → ACK)를 통해 연결을 수립하는 연결 지향형 프로토콜이다. 이를 통해 데이터의 순서 보장과 오류 복구가 가능하다.",
      "referencedPages": [3, 4]
    },
    {
      "number": 2,
      ...
    }
  ]
}
```

### 에러가 발생할 수 있는 지점

```
chatModel.call(...)
  │
  ├── 성공 → response.getResult().getOutput().getText() → JSON 문자열
  │           │
  │           ├── converter.convert(jsonText) 성공 → AIProblemSet 객체
  │           │
  │           └── converter.convert(jsonText) 실패
  │               → JSON은 왔지만 스키마 불일치
  │               → 예외: OutputConversionException  (← INVALID_AI_RESPONSE)
  │
  ├── 응답이 빈 텍스트 (null 또는 "")
  │   → 직접 체크 필요  (← NULL_AI_RESPONSE)
  │
  └── API 에러
      ├── 429 RESOURCE_EXHAUSTED → TransientAiException  (← AI_SERVER_TO_MANY_REQUEST)
      ├── 400 INVALID_ARGUMENT  → NonTransientAiException (← ClientSideException)
      └── 500 INTERNAL          → TransientAiException  (← AI_SERVER_RESPONSE_ERROR)
```

> 에러 핸들링은 Step 6에서 체계적으로 구현한다. 이번 Step에서는 정상 흐름만 확인한다.

---

## 5단계: FacadeService 수정 — 테스트용 통합

> 기존 `FacadeService`를 수정하여 Step 4의 Structured Output을 테스트한다.

### FacadeService.java 수정

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/service/FacadeService.java`

```java
package com.icc.qasker.ai.service;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.util.PromptBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * PDF 업로드 → 캐시 생성 → 프롬프트 조립 → Structured Output으로 퀴즈 생성.
     *
     * @param fileUrl      PDF URL (S3/CloudFront)
     * @param quizType     퀴즈 타입 ("MULTIPLE", "BLANK", "OX")
     * @param difficultyType 난이도 ("RECALL", "SKILLS", "STRATEGIC")
     * @param quizCount    생성할 문제 수
     * @param pageNumbers  참조할 페이지 번호 목록
     * @return 생성된 퀴즈 정보 Map
     */
    public Object generateQuiz(String fileUrl, String quizType, String difficultyType,
                               int quizCount, List<Integer> pageNumbers) {
        // ──── 1. PDF 업로드 (Step 2) ────
        FileMetadata metadata = geminiFileService.uploadPdf(fileUrl);
        log.info("업로드 완료: name={}, uri={}", metadata.name(), metadata.uri());

        String cacheName = null;
        try {
            // ──── 2. 캐시 생성 (Step 3) ────
            cacheName = geminiCacheService.createCache(metadata.uri());
            log.info("캐시 생성 완료: cacheName={}", cacheName);

            // ──── 3. 프롬프트 조립 (Step 4) ────
            String systemPrompt = PromptBuilder.build(
                quizType, difficultyType, quizCount, pageNumbers
            );
            log.info("프롬프트 조립 완료 (길이: {}자)", systemPrompt.length());

            // ──── 4. ChatModel 호출 + Structured Output (Step 4) ────
            ChatResponse response = chatModel.call(
                new Prompt(systemPrompt,
                    GoogleGenAiChatOptions.builder()
                        .useCachedContent(true)
                        .cachedContentName(cacheName)
                        .responseMimeType("application/json")
                        .build()
                ));

            String jsonText = response.getResult().getOutput().getText();
            log.info("Gemini 응답 수신 (길이: {}자)", jsonText.length());

            // ──── 5. JSON → AIProblemSet 역직렬화 ────
            BeanOutputConverter<AIProblemSet> converter = PromptBuilder.getConverter();
            AIProblemSet problemSet = converter.convert(jsonText);

            // ──── 6. 결과 로깅 ────
            for (AIProblem problem : problemSet.quiz()) {
                log.info("문제 {}: {} (선택지 {}개, 참조페이지: {})",
                    problem.number(), problem.title(),
                    problem.selections().size(), problem.referencedPages());
            }

            // ──── 7. 응답 구성 ────
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("cacheName", cacheName);
            result.put("quizType", quizType);
            result.put("difficultyType", difficultyType);
            result.put("requestedCount", quizCount);
            result.put("generatedCount", problemSet.quiz().size());
            result.put("quiz", problemSet.quiz());
            return result;

        } finally {
            // ──── 정리: 캐시 삭제 + 파일 삭제 ────
            geminiCacheService.deleteCache(cacheName);
            geminiFileService.deleteFile(metadata.name());
        }
    }
}
```

### AIController.java 수정

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/controller/AIController.java`

```java
// 기존 엔드포인트 유지 + 아래 추가

@Operation(summary = "PDF로 퀴즈를 생성한다 (Structured Output 테스트)")
@PostMapping("/test-quiz")
public ResponseEntity<?> testQuiz(
    @RequestParam String fileUrl,
    @RequestParam(defaultValue = "MULTIPLE") String quizType,
    @RequestParam(defaultValue = "RECALL") String difficultyType,
    @RequestParam(defaultValue = "3") int quizCount,
    @RequestParam List<Integer> pageNumbers
) {
    return ResponseEntity.ok(
        facadeService.generateQuiz(fileUrl, quizType, difficultyType, quizCount, pageNumbers)
    );
}
```

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
# 객관식 3문제 (RECALL 난이도)
curl -X POST "http://localhost:8080/ai/test-quiz?\
fileUrl=https://files.q-asker.com/실제파일.pdf&\
quizType=MULTIPLE&\
difficultyType=RECALL&\
quizCount=3&\
pageNumbers=1,2,3"

# 빈칸 2문제 (SKILLS 난이도)
curl -X POST "http://localhost:8080/ai/test-quiz?\
fileUrl=https://files.q-asker.com/실제파일.pdf&\
quizType=BLANK&\
difficultyType=SKILLS&\
quizCount=2&\
pageNumbers=5,6,7"

# OX 4문제 (STRATEGIC 난이도)
curl -X POST "http://localhost:8080/ai/test-quiz?\
fileUrl=https://files.q-asker.com/실제파일.pdf&\
quizType=OX&\
difficultyType=STRATEGIC&\
quizCount=4&\
pageNumbers=1,2,3,4,5"
```

### 기대 응답 (객관식 예시)

```json
{
  "cacheName": "cachedContents/abc123def456",
  "quizType": "MULTIPLE",
  "difficultyType": "RECALL",
  "requestedCount": 3,
  "generatedCount": 3,
  "quiz": [
    {
      "number": 1,
      "title": "TCP 프로토콜은 3-way handshake를 사용하여 연결을 수립한다. 다음 중 3-way handshake의 순서로 올바른 것은?",
      "selections": [
        { "content": "SYN → SYN-ACK → ACK", "correct": true },
        { "content": "ACK → SYN → SYN-ACK", "correct": false },
        { "content": "SYN → ACK → SYN-ACK", "correct": false },
        { "content": "SYN-ACK → SYN → ACK", "correct": false }
      ],
      "explanation": "TCP의 3-way handshake는 클라이언트가 SYN을 보내고, 서버가 SYN-ACK으로 응답하며, 클라이언트가 ACK을 보내는 순서로 진행된다.",
      "referencedPages": [3]
    },
    {
      "number": 2,
      ...
    },
    {
      "number": 3,
      ...
    }
  ]
}
```

### 기대 로그

```
INFO  GeminiFileService   - PDF 업로드 완료: name=files/r1b5ugz, state=PROCESSING
INFO  GeminiFileService   - 파일 처리 완료: name=files/r1b5ugz, uri=https://...
INFO  GeminiCacheService  - 캐시 생성 완료: name=cachedContents/abc123, ...
INFO  FacadeService       - 프롬프트 조립 완료 (길이: 1847자)
INFO  FacadeService       - Gemini 응답 수신 (길이: 2340자)
INFO  FacadeService       - 문제 1: TCP 프로토콜은... (선택지 4개, 참조페이지: [3])
INFO  FacadeService       - 문제 2: UDP는... (선택지 4개, 참조페이지: [4])
INFO  FacadeService       - 문제 3: ... (선택지 4개, 참조페이지: [3, 4])
INFO  GeminiCacheService  - 캐시 삭제 완료: name=cachedContents/abc123
INFO  GeminiFileService   - Gemini 파일 삭제 완료: name=files/r1b5ugz
```

### 검증 체크리스트

| 항목 | 확인 포인트 |
|---|---|
| **응답 형식** | JSON으로 파싱 가능한가? (에러 없이 AIProblemSet으로 변환) |
| **문제 수** | 요청한 quizCount와 실제 생성된 문제 수가 일치하는가? |
| **선택지 수** | MULTIPLE/BLANK → 4개, OX → 2개인가? |
| **정답 수** | 각 문제당 correct=true인 선택지가 정확히 1개인가? |
| **referencedPages** | 요청한 pageNumbers 범위 내의 페이지가 포함되는가? |
| **한국어** | 모든 텍스트가 한국어로 작성되었는가? |
| **해설** | explanation이 비어 있지 않고 의미 있는 설명인가? |
| **빈칸 타입** | BLANK일 때 title에 "_______"이 포함되는가? |
| **OX 타입** | OX일 때 선택지가 "O"와 "X"인가? |

---

## Gemini HTTP 메시지 — Structured Output 관련

> Step 3의 캐시 참조 요청에 `responseMimeType`이 추가된 형태.

### generateContent 요청 (Structured Output + 캐시 참조)

**Request**
```http
POST /v1beta/models/gemini-2.0-flash:generateContent?key={API_KEY} HTTP/1.1
Host: generativelanguage.googleapis.com
Content-Type: application/json

{
  "cachedContent": "cachedContents/abc123def456",
  "contents": [
    {
      "role": "user",
      "parts": [
        {
          "text": "당신은 대학 강의노트로부터 평가용 퀴즈를 생성하는 AI입니다.\n\n[작성 규칙]\n...\n\n[생성 지시]\n- 첨부된 강의노트의 [1, 2, 3] 페이지를 참고하여 정확히 3개의 문제를 생성하세요.\n...\n\n[객관식 문제 생성 지침]\n...\n\nYour response should be in JSON format.\n..."
        }
      ]
    }
  ],
  "generationConfig": {
    "responseMimeType": "application/json",
    "temperature": 0.7
  }
}
```

**Response**
```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8

{
  "candidates": [
    {
      "content": {
        "parts": [
          {
            "text": "{\"quiz\":[{\"number\":1,\"title\":\"TCP 프로토콜은...\",\"selections\":[...],\"explanation\":\"...\",\"referencedPages\":[3]}]}"
          }
        ],
        "role": "model"
      }
    }
  ],
  "usageMetadata": {
    "promptTokenCount": 16540,
    "candidatesTokenCount": 850,
    "totalTokenCount": 17390,
    "cachedContentTokenCount": 15234
  }
}
```

> `generationConfig.responseMimeType: "application/json"` — 이것이 `GoogleGenAiChatOptions.responseMimeType("application/json")`에 대응한다.
> 응답의 `text`가 순수 JSON 문자열이다 — 부가 텍스트 없음.

### HTTP ↔ Java 코드 매핑 (Step 4 추가분)

| HTTP / Gemini 개념 | Java (Spring AI) |
|---|---|
| `generationConfig.responseMimeType` | `GoogleGenAiChatOptions.builder().responseMimeType("application/json")` |
| 응답 `candidates[0].content.parts[0].text` | `response.getResult().getOutput().getText()` |
| JSON 문자열 → Java 객체 | `BeanOutputConverter.convert(jsonText)` |
| Java 클래스 → JSON Schema | `BeanOutputConverter.getFormat()` |

---

## BeanOutputConverter 상세 동작

### 내부 구조

```
┌──────────────────────────────────────────────────────────────┐
│  new BeanOutputConverter<>(AIProblemSet.class)               │
│                                                              │
│  ① 생성자에서 일어나는 일:                                     │
│     ObjectMapper objectMapper = new ObjectMapper();          │
│     JsonSchemaGenerator를 사용하여                            │
│     AIProblemSet.class → JSON Schema 생성                    │
│     @JsonPropertyDescription → Schema의 description으로      │
│                                                              │
│  ② getFormat() 호출 시:                                      │
│     "Your response should be in JSON format..."              │
│     + 생성된 JSON Schema를 문자열로 반환                       │
│     → 이걸 프롬프트에 추가                                     │
│                                                              │
│  ③ convert(String jsonText) 호출 시:                         │
│     objectMapper.readValue(jsonText, AIProblemSet.class)     │
│     → JSON 문자열을 AIProblemSet으로 역직렬화                  │
│     → 실패 시 OutputConversionException 발생                  │
└──────────────────────────────────────────────────────────────┘
```

### 왜 record를 사용하는가?

```java
// record는 Jackson이 기본 지원하는 불변 DTO
public record AIProblemSet(List<AIProblem> quiz) {}

// 위 코드가 자동으로 아래를 생성:
// - 생성자: AIProblemSet(List<AIProblem> quiz)  ← Jackson이 JSON 역직렬화 시 사용
// - getter: quiz()
// - equals(), hashCode(), toString()
```

> Jackson 2.12+는 record의 canonical constructor를 자동 인식한다.
> `@JsonCreator`나 `@JsonProperty` 없이도 역직렬화가 동작한다.
> `@JsonPropertyDescription`만 추가하면 BeanOutputConverter가 스키마를 생성한다.

### BeanOutputConverter vs 직접 ObjectMapper 사용

| | BeanOutputConverter | 직접 ObjectMapper |
|---|---|---|
| JSON Schema 생성 | `getFormat()` 자동 | 직접 작성 필요 |
| 역직렬화 | `convert()` 한 줄 | `objectMapper.readValue()` 직접 호출 |
| 프롬프트 통합 | 스키마를 프롬프트에 자동 포함 | 수동으로 스키마 문자열 관리 |
| 에러 처리 | `OutputConversionException` 통일 | 다양한 Jackson 예외 직접 처리 |

---

## 이후 Step에서의 사용 프리뷰 (Step 5: 병렬 배치 생성)

```
GeminiFileService.uploadPdf(pdfUrl)       ← Step 2
  → FileMetadata { name, uri }

GeminiCacheService.createCache(uri)        ← Step 3
  → cacheName

ChunkSplitter.createPageChunks(...)        ← Step 5
  → List<ChunkInfo> (각 청크: quizCount + referencedPages)

[병렬] 각 청크마다:                          ← Step 4 + 5
  String prompt = PromptBuilder.build(       ← Step 4 (이번 단계)
      quizType, difficultyType,
      chunk.quizCount, chunk.referencedPages
  );

  ChatResponse response = chatModel.call(
      new Prompt(prompt,
          GoogleGenAiChatOptions.builder()
              .useCachedContent(true)
              .cachedContentName(cacheName)   ← 모든 청크가 같은 캐시 참조
              .responseMimeType("application/json")  ← Step 4
              .build()
      ));

  AIProblemSet result = PromptBuilder.getConverter()
      .convert(response.getResult().getOutput().getText());  ← Step 4

  → 선택지 셔플 (MULTIPLE/BLANK)  ← Step 5
  → 번호 재할당                   ← Step 5
  → Consumer<AIProblemSet> 콜백    ← Step 5

finally:
  geminiCacheService.deleteCache(cacheName)  ← 정리
  geminiFileService.deleteFile(fileName)     ← 정리
```

---

## 참고 링크

- [Spring AI — Structured Output Converter](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html) — BeanOutputConverter, MapOutputConverter, ListOutputConverter
- [Spring AI — BeanOutputConverter Javadoc](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/converter/BeanOutputConverter.html) — getFormat(), convert() API
- [Gemini API — Structured Output (JSON)](https://ai.google.dev/gemini-api/docs/structured-output) — responseMimeType, responseSchema
- [Jackson — Record Support](https://github.com/FasterXML/jackson-databind#jdk-record-support) — Jackson의 Java record 지원
- [Spring AI — Google GenAI Chat Options](https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html) — GoogleGenAiChatOptions, responseMimeType
- Python 프롬프트 원본: `ai/app/prompt/core/multiple.py`, `blank.py`, `ox.py`
