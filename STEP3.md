# Step 3: Context Caching — GoogleGenAiCachedContentService

> 업로드된 PDF의 토큰을 서버 측에 캐싱하여, N개 병렬 요청이 같은 캐시를 참조하도록 한다.

---

## 전체 플로우

```
FileMetadata { name, uri }   ← Step 2에서 획득
       │
       ▼
┌──────────────────────────────┐
│  GeminiCacheService          │
│                              │
│  1. createCache()            │──▶ cacheService.create(CachedContentRequest)
│     └ Content + Part 조립     │     → GoogleGenAiCachedContent { name, ttl }
│                              │
│  2. (이후 Step 4~5에서 사용)    │──▶ chatModel.call(Prompt, GoogleGenAiChatOptions {
│     캐시 참조하여 질의           │       cachedContentName, useCachedContent })
│                              │
│  3. deleteCache()            │──▶ cacheService.delete(cacheName)
└──────────────────────────────┘
       │
       ▼
cacheName (예: "cachedContents/abc123")
→ 이후 Step 4~5에서 ChatModel 호출 시 참조
```

---

## 완성 후 디렉토리 구조

```
modules/ai/src/main/java/com/icc/qasker/ai/
├── config/
│   └── GeminiFileRestClientConfig.java   (기존 — Step 2)
├── controller/
│   └── AIController.java                 (기존 — 테스트용 엔드포인트 추가)
├── dto/
│   ├── ChatRequest.java                  (기존)
│   ├── GeminiFileUploadResponse.java     (기존 — Step 2)
│   └── MyChatResponse.java              (기존)
├── service/
│   ├── ChatService.java                  (기존)
│   ├── GeminiCacheService.java           ← NEW
│   └── GeminiFileService.java            (기존 — Step 2)
└── util/
    └── PdfUtils.java                     (기존 — Step 2)
```

---

## 배경: Context Caching이란?

### 캐싱 구조

```
                  ┌─────────────────────────┐
                  │   Gemini 서버            │
  캐시 생성 ──────▶│  CachedContent 저장      │
                  │  (PDF 토큰 미리 처리)      │
                  └──────────┬──────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
  chatModel.call()     chatModel.call()     chatModel.call()
  (청크 1, 3문제)       (청크 2, 3문제)       (청크 3, 3문제)
        │                    │                    │
        └─── 모두 cachedContentName으로 같은 캐시 참조 ──┘
             → PDF 토큰을 매번 보내지 않으므로 비용 절감
```

### 최소 토큰 제한

| 모델 | 최소 캐시 토큰 수 |
|---|---|
| Gemini 2.5 Flash | 1,024 |
| Gemini 2.5 Pro | 4,096 |
| Gemini 2.0 Flash | 4,096 |

> PDF 파일은 보통 수천~수만 토큰이므로 최소 제한에 걸리는 경우는 드물다.

### 캐시 보존 기간

- 기본 TTL: 생성 시 지정 (예: 10분)
- 미사용 시 만료되면 자동 삭제
- 수동 삭제: `cacheService.delete(cacheName)`

---

## Spring AI 캐시 API 구조

### 자동 구성 조건

`GoogleGenAiChatAutoConfiguration`에서 `GoogleGenAiCachedContentService` 빈이 자동 등록된다.

```java
@Bean
@ConditionalOnBean(GoogleGenAiChatModel.class)
@ConditionalOnMissingBean
@Conditional(CachedContentServiceCondition.class)
@ConditionalOnProperty(
    prefix = "spring.ai.google.genai.chat",
    name = "enable-cached-content",
    havingValue = "true",
    matchIfMissing = true        // ← 기본값이 true → 별도 설정 없이 자동 활성화
)
public GoogleGenAiCachedContentService googleGenAiCachedContentService(
    GoogleGenAiChatModel chatModel
) {
    return chatModel.getCachedContentService();
}
```

> `matchIfMissing = true`이므로 `application-local.yml`에 별도 설정 없이 바로 사용 가능하다.

### 핵심 클래스 관계

```
┌──────────────────────────────────────────┐
│ GoogleGenAiCachedContentService          │  ← @Autowired로 주입
│                                          │
│  create(CachedContentRequest) → GoogleGenAiCachedContent
│  get(name) → GoogleGenAiCachedContent    │
│  delete(name) → boolean                  │
│  list(pageSize, pageToken) → CachedContentPage
│  listAll() → List<GoogleGenAiCachedContent>
│  extendTtl(name, duration)               │
│  refreshExpiration(name, duration)        │
│  cleanupExpired() → int                  │
└──────────────────────────────────────────┘
         │ 생성 시 필요
         ▼
┌──────────────────────────────────────────┐
│ CachedContentRequest                     │  ← Builder 패턴
│                                          │
│  model: String                           │  예: "gemini-2.0-flash"
│  displayName: String                     │  예: "quiz-generation-uuid"
│  contents: List<Content>                 │  ← PDF fileData를 담는 핵심
│  systemInstruction: Content              │  (선택) 시스템 프롬프트
│  ttl: Duration                           │  예: Duration.ofMinutes(10)
│  expireTime: Instant                     │  (ttl 대신 절대 시각)
└──────────────────────────────────────────┘
         │ contents에 들어갈 타입
         ▼
┌──────────────────────────────────────────┐
│ Google GenAI SDK 타입 (com.google.genai.types)
│                                          │
│  Content.builder()                       │
│    .role("user")                         │
│    .parts(Part.builder()                 │
│      .fileData(FileData.builder()        │
│        .fileUri("https://...")            │  ← GeminiFileService에서 획득한 uri
│        .mimeType("application/pdf")      │
│        .build())                         │
│      .build())                           │
│    .build()                              │
└──────────────────────────────────────────┘
```

### 캐시 생성 → 참조 → 삭제 흐름

```
1. 캐시 생성
   CachedContentRequest request = CachedContentRequest.builder()
       .model("gemini-2.0-flash")
       .contents(List.of(pdfContent))
       .ttl(Duration.ofMinutes(10))
       .build();
   GoogleGenAiCachedContent cache = cacheService.create(request);
   String cacheName = cache.getName();  // "cachedContents/abc123"

2. 캐시 참조하여 질의 (Step 4~5에서 사용)
   ChatResponse response = chatModel.call(
       new Prompt("이 문서를 요약해줘",
           GoogleGenAiChatOptions.builder()
               .useCachedContent(true)
               .cachedContentName(cacheName)
               .build()
       ));

3. 캐시 삭제
   cacheService.delete(cacheName);
```

---

## 1단계: 서비스 — `GeminiCacheService.java` (핵심)

> PDF의 fileUri를 캐시에 등록하고 관리하는 서비스.

### 파일 생성

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/service/GeminiCacheService.java`

```java
package com.icc.qasker.ai.service;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.time.Duration;
import java.util.List;
import com.google.genai.types.Content;
import com.google.genai.types.FileData;
import com.google.genai.types.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.google.genai.cache.CachedContentRequest;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContent;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GeminiCacheService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final GoogleGenAiCachedContentService cacheService;
    private final String model;

    public GeminiCacheService(
        GoogleGenAiCachedContentService cacheService,
        @Value("${spring.ai.google.genai.chat.options.model}") String model
    ) {
        this.cacheService = cacheService;
        this.model = model;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Public API
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 업로드된 PDF 파일의 URI로 캐시를 생성한다.
     *
     * <pre>
     * ┌──────────────────── 캐시 생성 흐름 ────────────────────┐
     * │                                                        │
     * │ 1. Content 조립                                        │
     * │    Content.builder()                                   │
     * │      .role("user")                                     │
     * │      .parts(Part.builder()                             │
     * │        .fileData(FileData.builder()                    │
     * │          .fileUri(uploadedFileUri)                      │
     * │          .mimeType("application/pdf")                  │
     * │          .build())                                     │
     * │        .build())                                       │
     * │      .build()                                          │
     * │                                                        │
     * │ 2. CachedContentRequest 구성                           │
     * │    model: "gemini-2.0-flash" (yml에서 주입)             │
     * │    contents: [위에서 만든 Content]                      │
     * │    ttl: 10분                                            │
     * │                                                        │
     * │ 3. cacheService.create(request)                        │
     * │    ← GoogleGenAiCachedContent { name, ttl, ... }       │
     * └────────────────────────────────────────────────────────┘
     * </pre>
     *
     * @param fileUri  Gemini File API에서 획득한 파일 URI
     *                 (예: "https://generativelanguage.googleapis.com/v1beta/files/abc123")
     * @return 캐시 이름 (예: "cachedContents/abc123") — ChatModel 호출 시 참조
     */
    public String createCache(String fileUri) {
        try {
            // ──── 1. PDF fileData를 담은 Content 조립 ────
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

            // ──── 2. 캐시 생성 요청 구성 ────
            CachedContentRequest request = CachedContentRequest.builder()
                .model(model)
                .contents(List.of(pdfContent))
                .ttl(DEFAULT_TTL)
                .build();

            // ──── 3. 캐시 생성 ────
            GoogleGenAiCachedContent cache = cacheService.create(request);
            String cacheName = cache.getName();

            log.info("캐시 생성 완료: name={}, model={}, ttl={}, expireTime={}",
                cacheName, cache.getModel(), cache.getTtl(), cache.getExpireTime());

            return cacheName;
        } catch (Exception e) {
            log.error("캐시 생성 실패: fileUri={}, error={}", fileUri, e.getMessage());
            throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
        }
    }

    /**
     * 캐시를 삭제한다.
     * 실패 시 예외를 던지지 않고 경고 로그만 남긴다.
     *
     * @param cacheName 캐시 이름 (예: "cachedContents/abc123")
     */
    public void deleteCache(String cacheName) {
        if (cacheName == null) {
            return;
        }
        try {
            boolean deleted = cacheService.delete(cacheName);
            if (deleted) {
                log.info("캐시 삭제 완료: name={}", cacheName);
            } else {
                log.warn("캐시 삭제 실패 (이미 만료?): name={}", cacheName);
            }
        } catch (Exception e) {
            log.warn("캐시 삭제 중 오류 (무시): name={}, error={}", cacheName, e.getMessage());
        }
    }
}
```

### 포인트 정리

| 주제 | 설명 |
|---|---|
| **`@RequiredArgsConstructor` 미사용** | `@Value`로 model을 주입받으므로 명시적 생성자 사용 |
| **model 프로퍼티 재활용** | `spring.ai.google.genai.chat.options.model`로 ChatModel과 동일한 모델 사용 보장 |
| **DEFAULT_TTL = 10분** | PDF 기반 퀴즈 생성은 보통 1~2분이면 완료. 여유분 포함 10분 |
| **Content → Part → FileData** | Google GenAI SDK 타입을 직접 조립. Spring AI가 이 구조를 `CachedContentRequest.contents`에 담아 전달 |
| **deleteCache null-safe** | `finally` 블록에서 안전하게 호출 가능하도록 null 체크 |
| **에러 처리** | 생성 실패 → `CustomException`, 삭제 실패 → warn 로그만 (Step 2의 `deleteFile`과 동일 패턴) |

### 선행 설정 — `GeminiCacheConfig.java`

> `GeminiCacheService`가 주입받는 `GoogleGenAiCachedContentService` 빈이 자동 구성만으로는 등록되지 않을 수 있다.
> 이 설정 클래스를 먼저 추가해야 한다.

#### `GoogleGenAiCachedContentService`는 어떻게 빈이 되는가?

`GoogleGenAiCachedContentService` 클래스 자체에는 `@Component`나 `@Service` 어노테이션이 없다.
이 클래스는 **컴포넌트 스캔이 아니라 `@Bean` 메서드**를 통해 빈으로 등록된다.

```java
// GoogleGenAiChatAutoConfiguration.java (Spring AI 자동 구성 클래스)
@Bean   // ← 이것이 빈 등록을 담당
@ConditionalOnBean(GoogleGenAiChatModel.class)
@ConditionalOnMissingBean
@Conditional(CachedContentServiceCondition.class)
public GoogleGenAiCachedContentService googleGenAiCachedContentService(GoogleGenAiChatModel chatModel) {
    return chatModel.getCachedContentService();
}
```

Spring에서 빈을 등록하는 방식은 두 가지다:

| 방식 | 어노테이션 위치 | 예시 |
|---|---|---|
| **컴포넌트 스캔** | 클래스 자체에 `@Component`, `@Service` 등 | `GeminiCacheService` |
| **Java Config** | `@Configuration` 클래스의 `@Bean` 메서드 | `GoogleGenAiCachedContentService` |

`GoogleGenAiCachedContentService`는 후자 방식으로, 클래스 자체는 일반 POJO이고
`@Configuration` 클래스가 인스턴스를 생성해서 빈으로 등록해주는 구조다.
우리가 만드는 `GeminiCacheConfig`도 같은 방식이다.

#### 왜 필요한가?

위 자동 구성(`GoogleGenAiChatAutoConfiguration`)은 3단계 조건을 모두 통과해야 빈을 등록한다:

```
googleGenAiCachedContentService 빈 등록 조건:
  ① @ConditionalOnBean(GoogleGenAiChatModel.class)
  ② @Conditional(CachedContentServiceCondition.class)   ← 여기서 실패 가능
  ③ @ConditionalOnProperty(enable-cached-content, matchIfMissing=true)
```

**②번 조건**이 실패할 수 있다. `CachedContentServiceCondition`은 아래를 확인한다:

```java
// CachedContentServiceCondition.java (Spring AI 1.1.2 내부)
GoogleGenAiChatModel chatModel = context.getBeanFactory().getBean(GoogleGenAiChatModel.class);
if (chatModel.getCachedContentService() == null) {   // ← null이면 빈 등록 건너뜀
    return ConditionOutcome.noMatch(...);
}
```

그런데 `GoogleGenAiChatModel` 생성자에서 `cachedContentService`가 null로 설정될 수 있다:

```java
// GoogleGenAiChatModel.java (Spring AI 1.1.2 내부)
this.cachedContentService = (genAiClient != null
    && genAiClient.caches != null
    && genAiClient.async != null
    && genAiClient.async.caches != null)
    ? new GoogleGenAiCachedContentService(genAiClient)
    : null;   // ← Client의 caches 또는 async.caches가 null이면 null
```

실패 체인:

```
Client.caches == null (또는 Client.async.caches == null)
  → GoogleGenAiChatModel.getCachedContentService() == null
    → CachedContentServiceCondition 실패
      → GoogleGenAiCachedContentService 빈 등록 건너뜀
        → GeminiCacheService 생성 실패 (의존성 없음)
```

#### 파일 생성

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/config/GeminiCacheConfig.java`

```java
package com.icc.qasker.ai.config;

import com.google.genai.Client;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiCacheConfig {

    @Bean
    @ConditionalOnMissingBean
    public GoogleGenAiCachedContentService googleGenAiCachedContentService(Client genAiClient) {
        return new GoogleGenAiCachedContentService(genAiClient);
    }
}
```

| 포인트 | 설명 |
|---|---|
| **`Client` 직접 주입** | 자동 구성이 이미 등록한 `Client` 빈을 그대로 사용 |
| **`@ConditionalOnMissingBean`** | 자동 구성이 정상 동작하면 중복 등록 방지 |
| **`CachedContentServiceCondition` 우회** | `chatModel.getCachedContentService()` null 체크를 건너뛰고 직접 생성 |

---

## 2단계: Google GenAI SDK 타입 — Content/Part/FileData 조립 가이드

> `CachedContentRequest.contents`에 들어가는 타입은 Spring AI가 아니라 **Google GenAI SDK** (`com.google.genai.types`)의 타입이다.

### 타입 계층 구조

```
CachedContentRequest
  └── contents: List<Content>          ← com.google.genai.types.Content
        └── Content
              ├── role: Optional<String>        "user"
              └── parts: Optional<List<Part>>   ← com.google.genai.types.Part
                    └── Part
                          └── fileData: Optional<FileData>  ← com.google.genai.types.FileData
                                ├── fileUri: Optional<String>   "https://...files/abc123"
                                └── mimeType: Optional<String>  "application/pdf"
```

### Builder 사용법

```java
import com.google.genai.types.Content;
import com.google.genai.types.FileData;
import com.google.genai.types.Part;

// FileData: 업로드된 파일의 URI + MIME 타입
FileData fileData = FileData.builder()
    .fileUri("https://generativelanguage.googleapis.com/v1beta/files/abc123")
    .mimeType("application/pdf")
    .build();

// Part: FileData를 감싸는 래퍼
Part part = Part.builder()
    .fileData(fileData)
    .build();

// Content: role + parts 조합
Content content = Content.builder()
    .role("user")
    .parts(part)           // varargs: parts(Part... parts)
    .build();
```

### 주의: Optional 기반 API

Google GenAI SDK의 타입들은 내부적으로 `Optional`을 사용한다:

```java
// Content 클래스
public abstract Optional<List<Part>> parts();
public abstract Optional<String> role();

// FileData 클래스
public abstract Optional<String> fileUri();
public abstract Optional<String> mimeType();
```

하지만 Builder는 일반 값을 받으므로 사용 시 `Optional.of()`를 감쌀 필요 없다.

---

## 3단계: ChatModel에서 캐시 참조하기

> 캐시를 생성한 뒤, `GoogleGenAiChatOptions`에 `cachedContentName`을 설정하면 ChatModel이 해당 캐시를 참조한다.

### 캐시 참조 코드

```java
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

// cacheName: GeminiCacheService.createCache()에서 반환받은 값
// 예: "cachedContents/abc123"

ChatResponse response = chatModel.call(
    new Prompt("이 문서를 요약해줘",
        GoogleGenAiChatOptions.builder()
            .useCachedContent(true)
            .cachedContentName(cacheName)
            .build()
    ));

String text = response.getResult().getOutput().getText();
```

### GoogleGenAiChatOptions 캐시 관련 옵션

| 옵션 | 타입 | 설명 |
|---|---|---|
| `cachedContentName` | `String` | 참조할 캐시 이름 (예: `"cachedContents/abc123"`) |
| `useCachedContent` | `Boolean` | 캐시 사용 활성화 (`true`로 설정) |
| `autoCacheThreshold` | `Integer` | 자동 캐시 생성 토큰 임계값 (수동 생성 시 사용 안 함) |
| `autoCacheTtl` | `Duration` | 자동 캐시 TTL (수동 생성 시 사용 안 함) |

> 우리는 **수동 캐시** 방식을 사용한다. PDF를 명시적으로 캐시 생성 → cacheName 참조.
> `autoCacheThreshold`/`autoCacheTtl`은 자동 캐시용이므로 설정하지 않는다.

### 왜 수동 캐시인가?

```
[자동 캐시]
  chatModel.call() 시 자동으로 캐시 생성/참조
  → 요청마다 PDF 토큰을 보내야 하므로 첫 요청은 느림
  → 캐시 이름을 모르므로 삭제 관리 어려움

[수동 캐시] ← 우리가 사용하는 방식
  GeminiCacheService.createCache()로 명시적 생성
  → cacheName을 N개 병렬 요청에서 공유
  → 완료 후 명시적 삭제로 리소스 정리
  → 전체 파이프라인에서 캐시 생명주기 제어 가능
```

---

## 4단계: 검증

### 4-1. 컴파일 확인

```bash
./gradlew :ai:compileJava
```

### 4-2. 통합 테스트 (AIController에 임시 엔드포인트 추가)

```java
// AIController.java에 임시 추가 (테스트 후 제거)

@Autowired
private GeminiCacheService geminiCacheService;

@Autowired
private ChatModel chatModel;

@PostMapping("/test-cache")
public ResponseEntity<?> testCache(@RequestParam String pdfUrl) {
    // 1. PDF 업로드 (Step 2)
    var metadata = geminiFileService.uploadPdf(pdfUrl);
    log.info("업로드 완료: name={}, uri={}", metadata.name(), metadata.uri());

    String cacheName = null;
    try {
        // 2. 캐시 생성
        cacheName = geminiCacheService.createCache(metadata.uri());
        log.info("캐시 생성 완료: cacheName={}", cacheName);

        // 3. 캐시 참조하여 질의
        ChatResponse response = chatModel.call(
            new Prompt("이 문서의 핵심 내용을 3줄로 요약해줘.",
                GoogleGenAiChatOptions.builder()
                    .useCachedContent(true)
                    .cachedContentName(cacheName)
                    .build()
            ));
        String summary = response.getResult().getOutput().getText();
        log.info("요약 결과: {}", summary);

        // 4. 같은 캐시로 2번째 질문 (빠른지 확인)
        ChatResponse response2 = chatModel.call(
            new Prompt("이 문서에서 가장 중요한 개념 하나를 설명해줘.",
                GoogleGenAiChatOptions.builder()
                    .useCachedContent(true)
                    .cachedContentName(cacheName)
                    .build()
            ));
        String concept = response2.getResult().getOutput().getText();
        log.info("개념 설명: {}", concept);

        return ResponseEntity.ok(Map.of(
            "cacheName", cacheName,
            "summary", summary,
            "concept", concept
        ));
    } finally {
        // 5. 정리: 캐시 삭제 + 파일 삭제
        geminiCacheService.deleteCache(cacheName);
        geminiFileService.deleteFile(metadata.name());
    }
}
```

### 4-3. 테스트 실행

```bash
# 서버 시작
./gradlew bootRun --args='--spring.profiles.active=local'

# 캐시 테스트 (실제 S3/CloudFront URL 사용)
curl -X POST "http://localhost:8080/ai/test-cache?pdfUrl=https://files.q-asker.com/실제파일.pdf"
```

### 기대 응답

```json
{
  "cacheName": "cachedContents/abc123def456",
  "summary": "이 문서는 ...",
  "concept": "가장 중요한 개념은 ..."
}
```

### 기대 로그

```
INFO  GeminiFileService   - PDF 업로드 완료: name=files/r1b5ugz, state=PROCESSING
INFO  GeminiFileService   - 파일 처리 완료: name=files/r1b5ugz, uri=https://...
INFO  GeminiCacheService  - 캐시 생성 완료: name=cachedContents/abc123, model=..., ttl=PT10M, ...
INFO  AIController        - 요약 결과: 이 문서는 ...
INFO  AIController        - 개념 설명: 가장 중요한 개념은 ...
INFO  GeminiCacheService  - 캐시 삭제 완료: name=cachedContents/abc123
INFO  GeminiFileService   - Gemini 파일 삭제 완료: name=files/r1b5ugz
```

---

## Gemini Context Caching — HTTP 메시지 레퍼런스

> Spring AI가 내부적으로 Google GenAI SDK를 통해 전송하는 실제 HTTP 요청/응답.
> 직접 호출하지는 않지만, 디버깅 시 참고할 수 있다.

### 1) 캐시 생성

**Request**
```http
POST /v1beta/cachedContents?key={API_KEY} HTTP/1.1
Host: generativelanguage.googleapis.com
Content-Type: application/json

{
  "model": "models/gemini-2.0-flash",
  "displayName": "quiz-generation",
  "contents": [
    {
      "role": "user",
      "parts": [
        {
          "fileData": {
            "fileUri": "https://generativelanguage.googleapis.com/v1beta/files/r1b5ugz",
            "mimeType": "application/pdf"
          }
        }
      ]
    }
  ],
  "ttl": "600s"
}
```

**Response**
```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8

{
  "name": "cachedContents/abc123def456",
  "model": "models/gemini-2.0-flash",
  "displayName": "quiz-generation",
  "createTime": "2025-06-15T12:01:00.000000Z",
  "updateTime": "2025-06-15T12:01:00.000000Z",
  "expireTime": "2025-06-15T12:11:00.000000Z",
  "usageMetadata": {
    "totalTokenCount": 15234
  }
}
```

> `name: "cachedContents/abc123def456"` — 이것이 `cacheName`으로 사용됨.

---

### 2) 캐시 참조하여 generateContent 호출

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
          "text": "이 문서의 핵심 내용을 3줄로 요약해줘."
        }
      ]
    }
  ]
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
            "text": "이 문서는 ..."
          }
        ],
        "role": "model"
      }
    }
  ],
  "usageMetadata": {
    "promptTokenCount": 15240,
    "candidatesTokenCount": 87,
    "totalTokenCount": 15327,
    "cachedContentTokenCount": 15234
  }
}
```

> `cachedContentTokenCount: 15234` — PDF 토큰이 캐시에서 로드됨 (비용 절감).
> `promptTokenCount`에는 캐시 토큰이 포함되지만, **캐시된 토큰은 할인된 비용**으로 과금된다.

---

### 3) 캐시 삭제

**Request**
```http
DELETE /v1beta/cachedContents/abc123def456?key={API_KEY} HTTP/1.1
Host: generativelanguage.googleapis.com
```

**Response**
```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8

{}
```

---

## HTTP 메시지 ↔ Java 코드 매핑

| HTTP / Gemini 개념 | Java (Spring AI + Google GenAI SDK) |
|---|---|
| `POST /v1beta/cachedContents` + JSON | `cacheService.create(CachedContentRequest)` |
| `contents[].parts[].fileData.fileUri` | `FileData.builder().fileUri(uri).build()` |
| `contents[].parts[].fileData.mimeType` | `FileData.builder().mimeType("application/pdf").build()` |
| `contents[].role` | `Content.builder().role("user").build()` |
| `ttl: "600s"` | `CachedContentRequest.builder().ttl(Duration.ofMinutes(10))` |
| 응답 `name: "cachedContents/..."` | `cache.getName()` |
| `generateContent` + `cachedContent` 필드 | `GoogleGenAiChatOptions.builder().cachedContentName(name).useCachedContent(true)` |
| `DELETE /v1beta/cachedContents/{name}` | `cacheService.delete(name)` |

---

## 에러 핸들링 매핑

| 시나리오 | ExceptionMessage | HTTP Status |
|---|---|---|
| 캐시 생성 실패 (API 오류) | `AI_SERVER_RESPONSE_ERROR` | 500 |
| 캐시 생성 실패 (최소 토큰 미달) | `AI_SERVER_RESPONSE_ERROR` | 500 |
| 캐시 참조 질의 실패 (만료된 캐시) | `AI_SERVER_RESPONSE_ERROR` | 500 |
| 캐시 삭제 실패 | warn 로그만 (예외 전파 X) | — |

---

## 이후 Step에서의 사용 프리뷰 (Step 5: 파이프라인)

```
GeminiFileService.uploadPdf(pdfUrl)       ← Step 2
  → FileMetadata { name, uri }

GeminiCacheService.createCache(uri)        ← Step 3 (이번 단계)
  → cacheName

ChunkSplitter.createPageChunks(...)        ← Step 5
  → List<ChunkInfo>

[병렬] 각 청크마다:                          ← Step 4~5
  chatModel.call(prompt, GoogleGenAiChatOptions {
      cachedContentName = cacheName,        ← 모든 청크가 같은 캐시 참조
      useCachedContent = true
  })

finally:
  geminiCacheService.deleteCache(cacheName)  ← 정리
  geminiFileService.deleteFile(fileName)     ← 정리
```

---

## 참고 링크

- [Spring AI — Google GenAI Context Caching](https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html#_context_caching) — GoogleGenAiCachedContentService, CachedContentRequest
- [Gemini API — Context Caching](https://ai.google.dev/gemini-api/docs/caching) — CachedContent 생성/참조/TTL, 최소 토큰 요건
- [Google GenAI Java SDK — Content/Part/FileData](https://github.com/googleapis/java-genai) — SDK 타입 구조 (캐시에 PDF fileData 전달 시 참조)
- [Spring AI GitHub — CachedContentRequest.java 소스](https://github.com/spring-projects/spring-ai/blob/main/models/spring-ai-google-genai/src/main/java/org/springframework/ai/google/genai/cache/CachedContentRequest.java)
