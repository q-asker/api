# Step 2: Gemini File API — PDF 업로드 (RestClient 직접 구현)

> Spring AI가 File Upload API를 지원하지 않으므로, RestClient로 직접 구현한다.

---

## 전체 플로우

```
CloudFront PDF URL
       │
       ▼
  ┌─────────────┐
  │  PdfUtils   │  downloadToTemp(url) → 임시 파일 Path
  └──────┬──────┘
         │ byte[]
         ▼
  ┌──────────────────────┐
  │  GeminiFileService   │
  │                      │
  │  1. initiateUpload() │──▶ POST /upload/v1beta/files  (세션 URL 획득)
  │  2. uploadBytes()    │──▶ POST {세션URL}             (PDF 바이트 전송)
  │  3. waitForProcessing│──▶ GET  /v1beta/files/{name}  (ACTIVE 대기)
  │  4. deleteFile()     │──▶ DELETE /v1beta/files/{name} (정리)
  └──────────────────────┘
         │
         ▼
  FileMetadata { name, uri, state }
  → 이후 Step 3 (Context Caching)에서 uri 참조
```

---

## 완성 후 디렉토리 구조

```
modules/ai/src/main/java/com/icc/qasker/ai/
├── config/
│   └── GeminiFileRestClientConfig.java   ← NEW
├── controller/
│   └── ChatController.java               (기존)
├── dto/
│   ├── ChatRequest.java                   (기존)
│   ├── MyChatResponse.java                (기존)
│   └── gemini/
│       └── GeminiFileUploadResponse.java  ← NEW
├── service/
│   ├── ChatService.java                   (기존)
│   └── GeminiFileService.java             ← NEW
└── util/
    └── PdfUtils.java                      ← NEW
```

---

## 1단계: DTO — `GeminiFileUploadResponse.java`

> 의존성이 없는 순수 데이터 클래스부터 만든다.

### Gemini API 응답 JSON 구조

```json
{
  "file": {
    "name": "files/abc123def",
    "displayName": "lecture.pdf",
    "mimeType": "application/pdf",
    "sizeBytes": "1234567",
    "createTime": "2025-01-01T00:00:00.000000Z",
    "updateTime": "2025-01-01T00:00:00.000000Z",
    "state": "PROCESSING",
    "uri": "https://generativelanguage.googleapis.com/v1beta/files/abc123def"
  }
}
```

### 파일 생성

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/dto/gemini/GeminiFileUploadResponse.java`

```java
package com.icc.qasker.ai.dto.gemini;

/**
 * Gemini File API 업로드/조회 응답 매핑.
 * JSON 구조: { "file": { ... } }
 */
public record GeminiFileUploadResponse(
    FileMetadata file
) {

    /**
     * Gemini 파일 메타데이터.
     *
     * @param name        파일 리소스 이름 (예: "files/abc123") — 조회/삭제 시 식별자
     * @param displayName 업로드 시 지정한 표시 이름
     * @param mimeType    MIME 타입 (예: "application/pdf")
     * @param sizeBytes   파일 크기 (Gemini가 문자열로 반환)
     * @param createTime  생성 시각 (ISO 8601)
     * @param updateTime  수정 시각 (ISO 8601)
     * @param state       처리 상태: "PROCESSING" | "ACTIVE" | "FAILED"
     * @param uri         파일 URI — generateContent/Cache에서 fileUri로 참조
     */
    public record FileMetadata(
        String name,
        String displayName,
        String mimeType,
        String sizeBytes,
        String createTime,
        String updateTime,
        String state,
        String uri
    ) {

    }
}
```

### 포인트

- `sizeBytes`가 `String`인 이유: Gemini API가 JSON 문자열(`"1234567"`)로 반환
- Upload 응답과 GET 조회 응답이 동일한 구조이므로 하나의 DTO로 재사용
- 중첩 record (`FileMetadata`)로 `{ "file": { ... } }` 래퍼 구조를 자연스럽게 매핑

---

## 2단계: 유틸 — `PdfUtils.java`

> CloudFront URL에서 PDF를 임시 파일로 다운로드하는 유틸리티.

### 파일 생성

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/util/PdfUtils.java`

```java
package com.icc.qasker.ai.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PdfUtils {

    /**
     * URL에서 PDF를 다운로드하여 임시 파일로 저장한다.
     *
     * @param pdfUrl CloudFront URL (예: "https://files.q-asker.com/...")
     * @return 다운로드된 임시 파일의 Path
     * @throws IOException 다운로드 실패 시
     */
    public Path downloadToTemp(String pdfUrl) throws IOException {
        Path tempFile = Files.createTempFile("gemini-upload-", ".pdf");

        log.debug("PDF 다운로드 시작: {} → {}", pdfUrl, tempFile);

        try (InputStream in = URI.create(pdfUrl).toURL().openStream()) {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // 다운로드 실패 시 빈 temp file도 정리
            deleteTempFile(tempFile);
            throw e;
        }

        log.debug("PDF 다운로드 완료: {} bytes", Files.size(tempFile));
        return tempFile;
    }

    /**
     * 임시 파일을 삭제한다. 삭제 실패 시 예외를 던지지 않고 경고 로그만 남긴다.
     *
     * @param tempFile 삭제할 파일 경로 (null이면 무시)
     */
    public void deleteTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            boolean deleted = Files.deleteIfExists(tempFile);
            if (deleted) {
                log.debug("임시 파일 삭제 완료: {}", tempFile);
            }
        } catch (IOException e) {
            log.warn("임시 파일 삭제 실패: {} — {}", tempFile, e.getMessage());
        }
    }
}
```

### 포인트

- `URI.create(pdfUrl).toURL().openStream()`: 순수 JDK API로 단순 다운로드 (CloudFront URL은 인증 불필요)
- `createTempFile("gemini-upload-", ".pdf")`: OS temp 디렉토리에 `gemini-upload-12345.pdf` 형태로 생성
- `deleteTempFile`은 null-safe + 예외 억제 → `finally` 블록에서 안전하게 호출 가능

---

## 3단계: 설정 — `GeminiFileRestClientConfig.java`

> Gemini File API 전용 RestClient 빈을 등록한다.

### 파일 생성

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/config/GeminiFileRestClientConfig.java`

```java
package com.icc.qasker.ai.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class GeminiFileRestClientConfig {

    /**
     * Gemini File API 전용 RestClient.
     * <p>
     * Base URL은 호스트만 설정한다.
     * - 업로드: /upload/v1beta/files
     * - 조회/삭제: /v1beta/files/{name}
     * 경로가 다르므로 각 메서드에서 개별 지정한다.
     */
    @Bean("geminiFileRestClient")
    public RestClient geminiFileRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(120)); // 대용량 PDF 업로드 대비

        return RestClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .requestFactory(factory)
            .build();
    }
}
```

### 포인트

- `SimpleClientHttpRequestFactory` 사용: 스트리밍이나 커넥션 풀이 불필요한 단순 REST 호출
- `readTimeout = 120초`: PDF 파일 업로드(Step 2의 바이트 전송)는 파일 크기에 따라 시간이 걸릴 수 있음
- API 키는 여기서 설정하지 않음 → `GeminiFileService`에서 `@Value`로 주입받아 요청별 쿼리 파라미터로 전달
- 빈 이름 `"geminiFileRestClient"`: quiz 모듈의 `"aiRestClient"`, `"aiStreamClient"`와 충돌 방지

---

## 4단계: 서비스 — `GeminiFileService.java` (핵심)

> Resumable Upload 프로토콜을 구현하는 핵심 서비스.

### 파일 생성

**경로**: `modules/ai/src/main/java/com/icc/qasker/ai/service/GeminiFileService.java`

```java
package com.icc.qasker.ai.service;

import com.icc.qasker.ai.dto.gemini.GeminiFileUploadResponse;
import com.icc.qasker.ai.dto.gemini.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.util.PdfUtils;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class GeminiFileService {

    private final RestClient restClient;
    private final String apiKey;
    private final PdfUtils pdfUtils;

    // ── 폴링 설정 ──
    private static final int POLL_INTERVAL_MS = 2_000;     // 2초
    private static final int MAX_POLL_ATTEMPTS = 30;        // 최대 30회 = 60초
    private static final String STATE_ACTIVE = "ACTIVE";
    private static final String STATE_FAILED = "FAILED";

    public GeminiFileService(
        @Qualifier("geminiFileRestClient") RestClient restClient,
        @Value("${spring.ai.google.genai.api-key}") String apiKey,
        PdfUtils pdfUtils
    ) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.pdfUtils = pdfUtils;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Public API
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * PDF URL → Gemini File API 업로드 → ACTIVE 대기 → FileMetadata 반환.
     *
     * <pre>
     * ┌──────────────────── HTTP 플로우 요약 ────────────────────┐
     * │                                                          │
     * │ 1. GET https://files.q-asker.com/xxx.pdf  → 임시 파일    │
     * │                                                          │
     * │ 2. POST /upload/v1beta/files?key=...                     │
     * │    X-Goog-Upload-Protocol: resumable                     │
     * │    X-Goog-Upload-Command: start                          │
     * │    ← 응답 헤더: x-goog-upload-url: {세션URL}             │
     * │                                                          │
     * │ 3. POST {세션URL}                                        │
     * │    X-Goog-Upload-Command: upload, finalize               │
     * │    Body: raw PDF bytes (스트리밍)                         │
     * │    ← 응답: { "file": { "state":"PROCESSING", ... } }    │
     * │                                                          │
     * │ 4. GET /v1beta/files/{name}?key=...  (2초 간격 반복)     │
     * │    ← 응답: { "file": { "state":"ACTIVE", "uri":... } }  │
     * └──────────────────────────────────────────────────────────┘
     * </pre>
     *
     * @param pdfUrl CloudFront PDF URL
     * @return ACTIVE 상태의 파일 메타데이터 (name, uri 포함)
     */
    public FileMetadata uploadPdf(String pdfUrl) {
        Path tempFile = null;
        try {
            // ──── 1. PDF 다운로드 → 임시 파일 ────
            // HTTP: GET https://files.q-asker.com/abc/lecture.pdf
            //   ← 200 OK (application/pdf, 1,572,864 bytes)
            tempFile = pdfUtils.downloadToTemp(pdfUrl);
            long fileSize = Files.size(tempFile);

            // ──── 2. Resumable Upload Step 1: 세션 시작 ────
            // HTTP: POST /upload/v1beta/files?key={API_KEY}
            //   Content-Type: application/json
            //   X-Goog-Upload-Protocol: resumable
            //   X-Goog-Upload-Command: start
            //   X-Goog-Upload-Header-Content-Type: application/pdf
            //   X-Goog-Upload-Header-Content-Length: 1572864
            //   Body: { "file": { "display_name": "lecture.pdf" } }
            //   ← 200 OK  (바디 없음)
            //   ← x-goog-upload-url: https://...?uploadType=resumable&upload_id=ADPycdv...
            String uploadSessionUrl = initiateUpload(fileSize, extractFileName(pdfUrl));

            // ──── 3. Resumable Upload Step 2: 파일 바이트 스트리밍 전송 ────
            // HTTP: POST https://...?uploadType=resumable&upload_id=ADPycdv...
            //   Content-Type: application/octet-stream  ← FileSystemResource → raw 바이너리 전송
            //   Content-Length: 1572864
            //   Accept: application/json                ← .body(Class) → 응답은 JSON으로 받겠다
            //   X-Goog-Upload-Command: upload, finalize
            //   X-Goog-Upload-Offset: 0
            //   Body: << raw PDF binary stream >>
            //   ← 200 OK  Content-Type: application/json
            //   ← { "file": { "name":"files/r1b5ugz", "state":"PROCESSING", ... } }
            GeminiFileUploadResponse response = uploadBytes(uploadSessionUrl, tempFile, fileSize);
            String fileName = response.file().name();
            log.info("PDF 업로드 완료: name={}, state={}", fileName, response.file().state());

            // ──── 4. 상태 폴링: PROCESSING → ACTIVE 대기 ────
            // HTTP: GET /v1beta/files/r1b5ugz?key={API_KEY}  (2초 간격 반복)
            //   Accept: application/json        ← RestClient가 .body(Class) 호출 시 자동 설정
            //   ← 200 OK  Content-Type: application/json
            //   ← { "file": { "state":"PROCESSING" } }   // 1회차
            //   ← { "file": { "state":"PROCESSING" } }   // 2회차
            //   ← { "file": { "state":"ACTIVE", "uri":"https://..." } }  // 완료!
            FileMetadata metadata = waitForProcessing(fileName);
            log.info("파일 처리 완료: name={}, uri={}", metadata.name(), metadata.uri());
            return metadata;
        } catch (IOException e) {
            log.error("PDF 업로드 중 I/O 오류: {}", e.getMessage());
            throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("PDF 처리 대기 중 인터럽트 발생");
            throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
        } finally {
            // 5. 임시 파일 정리 (성공/실패 무관)
            pdfUtils.deleteTempFile(tempFile);
        }
    }

    /**
     * Gemini File API에서 파일을 삭제한다.
     * 실패 시 예외를 던지지 않고 경고 로그만 남긴다.
     *
     * @param fileName 파일 리소스 이름 (예: "files/abc123")
     */
    public void deleteFile(String fileName) {
        try {
            restClient.delete()
                .uri("/v1beta/{name}?key={key}", fileName, apiKey)
                .retrieve()
                .toBodilessEntity();

            log.info("Gemini 파일 삭제 완료: name={}", fileName);
        } catch (Exception e) {
            log.warn("Gemini 파일 삭제 실패 (무시): name={}, error={}", fileName, e.getMessage());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Resumable Upload 내부 구현
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Resumable Upload Step 1: 업로드 세션 시작 → 세션 URL 반환.
     *
     * <pre>
     * POST /upload/v1beta/files?key={apiKey}
     * Headers:
     *   X-Goog-Upload-Protocol: resumable
     *   X-Goog-Upload-Command: start
     *   X-Goog-Upload-Header-Content-Type: application/pdf
     *   X-Goog-Upload-Header-Content-Length: {fileSize}
     *   Content-Type: application/json
     * Body: { "file": { "display_name": "..." } }
     *
     * Response Header → x-goog-upload-url: {세션URL}
     * </pre>
     */
    private String initiateUpload(long fileSize, String displayName) {
        var requestBody = Map.of(
            "file", Map.of("display_name", displayName)
        );

        // exchange()를 사용하는 이유: 응답 헤더에서 업로드 세션 URL을 추출해야 하기 때문
        // retrieve()는 body만 반환하므로 헤더 접근 불가
        return restClient.post()
            .uri("/upload/v1beta/files?key={key}", apiKey)
            .header("X-Goog-Upload-Protocol", "resumable")
            .header("X-Goog-Upload-Command", "start")
            .header("X-Goog-Upload-Header-Content-Type", "application/pdf")
            .header("X-Goog-Upload-Header-Content-Length", String.valueOf(fileSize))
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .exchange((request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
                }
                String url = response.getHeaders().getFirst("x-goog-upload-url");
                if (url == null || url.isBlank()) {
                    throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
                }
                return url;
            });
    }

    /**
     * Resumable Upload Step 2: 세션 URL에 파일 바이트를 전송.
     *
     * <pre>
     * POST {uploadSessionUrl}    ← 절대 URL
     * Headers:
     *   X-Goog-Upload-Command: upload, finalize
     *   X-Goog-Upload-Offset: 0
     * Body: raw PDF bytes
     *
     * Response: { "file": { "name": "...", "state": "PROCESSING", ... } }
     * </pre>
     */
    private GeminiFileUploadResponse uploadBytes(String uploadSessionUrl, Path pdfFile,
        long fileSize) {
        // FileSystemResource: 파일을 메모리에 전부 올리지 않고 InputStream으로 스트리밍 전송
        // → 35MB PDF도 힙 부담 없이 전송 가능
        // 세션 URL은 절대 URL → baseUrl이 없는 RestClient.create()로 호출
        return RestClient.create().post()
            .uri(URI.create(uploadSessionUrl))
            .header("X-Goog-Upload-Command", "upload, finalize")
            .header("X-Goog-Upload-Offset", "0")
            .header("Content-Length", String.valueOf(fileSize))
            .body(new FileSystemResource(pdfFile))
            .retrieve()
            .body(GeminiFileUploadResponse.class);
    }

    /**
     * 파일 상태를 폴링하여 ACTIVE가 될 때까지 대기한다.
     *
     * <pre>
     * GET /v1beta/{fileName}?key={apiKey}
     *
     * 폴링 전략:
     *   - 2초 간격, 최대 30회 (= 60초 타임아웃)
     *   - ACTIVE → 즉시 반환
     *   - FAILED → 즉시 예외
     *   - PROCESSING → sleep 후 재시도
     * </pre>
     *
     * @param fileName 파일 리소스 이름 (예: "files/abc123")
     * @return ACTIVE 상태의 파일 메타데이터
     */
    private FileMetadata waitForProcessing(String fileName) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
            GeminiFileUploadResponse response = getFile(fileName);
            String state = response.file().state();

            log.debug("파일 상태 폴링 [{}/{}]: name={}, state={}",
                attempt, MAX_POLL_ATTEMPTS, fileName, state);

            if (STATE_ACTIVE.equals(state)) {
                return response.file();
            }

            if (STATE_FAILED.equals(state)) {
                log.error("Gemini 파일 처리 실패: name={}", fileName);
                throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
            }

            Thread.sleep(POLL_INTERVAL_MS);
        }

        log.error("파일 처리 타임아웃: name={}, {}ms * {} attempts",
            fileName, POLL_INTERVAL_MS, MAX_POLL_ATTEMPTS);
        throw new CustomException(ExceptionMessage.AI_SERVER_TIMEOUT);
    }

    /**
     * Gemini File API에서 파일 메타데이터를 조회한다.
     */
    private GeminiFileUploadResponse getFile(String fileName) {
        return restClient.get()
            .uri("/v1beta/{name}?key={key}", fileName, apiKey)
            .retrieve()
            .body(GeminiFileUploadResponse.class);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 유틸
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * URL에서 파일명을 추출한다.
     * 예: "https://files.q-asker.com/abc/lecture.pdf" → "lecture.pdf"
     */
    private String extractFileName(String url) {
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            String name = url.substring(lastSlash + 1);
            int queryIdx = name.indexOf('?');
            return queryIdx > 0 ? name.substring(0, queryIdx) : name;
        }
        return "uploaded.pdf";
    }
}
```

### 포인트 정리

| 주제                               | 설명                                                                         |
|----------------------------------|----------------------------------------------------------------------------|
| **생성자 주입**                       | `@Qualifier`가 필요해서 `@RequiredArgsConstructor` 대신 명시적 생성자 사용                |
| **API 키**                        | `@Value("${spring.ai.google.genai.api-key}")`로 Spring AI 프로퍼티 재활용          |
| **`exchange()` vs `retrieve()`** | `initiateUpload`에서 응답 **헤더**(`x-goog-upload-url`)를 읽어야 하므로 `exchange()` 필수 |
| **`FileSystemResource`**         | `uploadBytes`에서 `byte[]` 대신 스트리밍 전송 → PDF 전체를 힙에 올리지 않아 OOM 방지             |
| **`RestClient.create()`**        | `uploadBytes`에서 세션 URL이 절대 URL이므로 baseUrl 없는 새 RestClient 사용               |
| **폴링 전략**                        | 2초 고정 간격 × 30회 = 60초. PDF 처리는 보통 5~15초에 완료                                 |
| **`try-finally`**                | `uploadPdf`에서 성공/실패 무관하게 임시 파일 항상 정리                                       |
| **InterruptedException**         | interrupt flag 복원 → Virtual Thread(Step 5)에서 취소 시 안전                       |

---

## 5단계: 검증

### 5-1. 컴파일 확인

```bash
./gradlew :ai:compileJava
```

### 5-2. 통합 테스트 (ChatController에 임시 엔드포인트 추가)

```java
// ChatController.java에 임시 추가 (테스트 후 제거)

@Autowired
private GeminiFileService geminiFileService;

@PostMapping("/test-upload")
public ResponseEntity<?> testUpload(@RequestParam String pdfUrl) {
    // 1. 업로드 + ACTIVE 대기
    var metadata = geminiFileService.uploadPdf(pdfUrl);

    // 2. 결과 확인: state=ACTIVE, uri != null
    log.info("name={}, state={}, uri={}", metadata.name(), metadata.state(), metadata.uri());

    // 3. (선택) ChatModel에 fileUri로 질의 — Step 3 프리뷰
    // ChatResponse response = chatModel.call(new Prompt("이 문서를 요약해줘", ...));

    // 4. 정리
    geminiFileService.deleteFile(metadata.name());

    return ResponseEntity.ok(metadata);
}
```

### 5-3. 테스트 실행

```bash
# 서버 시작
./gradlew bootRun --args='--spring.profiles.active=local'

# PDF 업로드 테스트 (실제 S3/CloudFront URL 사용)
curl -X POST "http://localhost:8080/ai/test-upload?pdfUrl=https://files.q-asker.com/실제파일.pdf"
```

### 기대 응답

```json
{
  "name": "files/abc123def456",
  "displayName": "실제파일.pdf",
  "mimeType": "application/pdf",
  "sizeBytes": "1234567",
  "createTime": "...",
  "updateTime": "...",
  "state": "ACTIVE",
  "uri": "https://generativelanguage.googleapis.com/v1beta/files/abc123def456"
}
```

---

## Gemini File API — HTTP 메시지 레퍼런스

> 실제 네트워크를 타는 raw HTTP 요청/응답을 그대로 보여준다.
> `{API_KEY}` 자리에 `spring.ai.google.genai.api-key` 값이 들어간다.

### 1) Resumable Upload — Step 1: Initiate (세션 시작)

**Request**
```http
POST /upload/v1beta/files?key={API_KEY} HTTP/1.1
Host: generativelanguage.googleapis.com
Content-Type: application/json
X-Goog-Upload-Protocol: resumable
X-Goog-Upload-Command: start
X-Goog-Upload-Header-Content-Type: application/pdf
X-Goog-Upload-Header-Content-Length: 1572864

{
  "file": {
    "display_name": "lecture.pdf"
  }
}
```

**Response**
```http
HTTP/1.1 200 OK
x-goog-upload-url: https://generativelanguage.googleapis.com/upload/v1beta/files?uploadType=resumable&upload_id=ADPycdv...
x-goog-upload-status: active
Content-Length: 0
```

> 핵심: 응답 **바디는 비어 있고**, `x-goog-upload-url` 헤더에 세션 URL이 담긴다.
> Java에서 `exchange()`를 쓰는 이유가 바로 이것 — `retrieve()`로는 헤더에 접근할 수 없다.

---

### 2) Resumable Upload — Step 2: Upload Bytes (파일 전송)

**Request**
```http
POST /upload/v1beta/files?uploadType=resumable&upload_id=ADPycdv... HTTP/1.1
Host: generativelanguage.googleapis.com
Content-Type: application/octet-stream
Content-Length: 1572864
Accept: application/json
X-Goog-Upload-Command: upload, finalize
X-Goog-Upload-Offset: 0

<< 1,572,864 bytes of raw PDF binary data >>
```

**Response**
```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8

{
  "file": {
    "name": "files/r1b5ugzynv11",
    "displayName": "lecture.pdf",
    "mimeType": "application/pdf",
    "sizeBytes": "1572864",
    "createTime": "2025-06-15T12:00:00.000000Z",
    "updateTime": "2025-06-15T12:00:00.000000Z",
    "state": "PROCESSING",
    "uri": "https://generativelanguage.googleapis.com/v1beta/files/r1b5ugzynv11",
    "expirationTime": "2025-06-17T12:00:00.000000Z"
  }
}
```

> `state: "PROCESSING"` — 아직 사용 불가. 폴링이 필요하다.

---

### 3) 파일 상태 조회 (폴링)

**Request**
```http
GET /v1beta/files/r1b5ugzynv11?key={API_KEY} HTTP/1.1
Host: generativelanguage.googleapis.com
Accept: application/json
```

**Response (처리 중)**
```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8

{
  "name": "files/r1b5ugzynv11",
  "displayName": "lecture.pdf",
  "mimeType": "application/pdf",
  "sizeBytes": "1572864",
  "createTime": "2025-06-15T12:00:00.000000Z",
  "updateTime": "2025-06-15T12:00:01.000000Z",
  "state": "PROCESSING",
  "uri": "https://generativelanguage.googleapis.com/v1beta/files/r1b5ugzynv11"
}
```

**Response (처리 완료 — 수 초 후)**
```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8

{
  "name": "files/r1b5ugzynv11",
  "displayName": "lecture.pdf",
  "mimeType": "application/pdf",
  "sizeBytes": "1572864",
  "createTime": "2025-06-15T12:00:00.000000Z",
  "updateTime": "2025-06-15T12:00:08.000000Z",
  "state": "ACTIVE",
  "uri": "https://generativelanguage.googleapis.com/v1beta/files/r1b5ugzynv11"
}
```

> `state: "ACTIVE"` → 이제 `uri`를 generateContent나 Context Cache에서 참조할 수 있다.

---

### 4) 파일 삭제

**Request**
```http
DELETE /v1beta/files/r1b5ugzynv11?key={API_KEY} HTTP/1.1
Host: generativelanguage.googleapis.com
Accept: application/json
```

**Response**
```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8

{}
```

---

### HTTP 메시지 ↔ Java 코드 매핑

| HTTP 메시지 | Java (RestClient) |
|---|---|
| `POST /upload/v1beta/files?key=...` | `restClient.post().uri("/upload/v1beta/files?key={key}", apiKey)` |
| `X-Goog-Upload-Protocol: resumable` | `.header("X-Goog-Upload-Protocol", "resumable")` |
| `Content-Type: application/json` + JSON body | `.contentType(MediaType.APPLICATION_JSON).body(requestBody)` |
| 응답 헤더 `x-goog-upload-url` 읽기 | `.exchange((req, res) -> res.getHeaders().getFirst("x-goog-upload-url"))` |
| `POST {세션URL}` + raw bytes | `RestClient.create().post().uri(URI.create(sessionUrl)).body(new FileSystemResource(path))` |
| `GET /v1beta/files/{name}?key=...` | `restClient.get().uri("/v1beta/{name}?key={key}", name, apiKey)` |
| 응답 JSON → Java 객체 | `.retrieve().body(GeminiFileUploadResponse.class)` |
| `DELETE /v1beta/files/{name}?key=...` | `restClient.delete().uri("/v1beta/{name}?key={key}", name, apiKey)` |

---

### 파일 상태 전이

```
PROCESSING ──(수 초)──▶ ACTIVE    (정상: 사용 가능)
                     ╲
                      ╲──▶ FAILED   (비정상: 파일 손상 등)
```

### 파일 보존 기간

- 업로드 후 **48시간** 자동 삭제
- 프로젝트당 저장 한도: 20GB
- 파일당 최대 크기: 2GB

---

## 에러 핸들링 매핑

| 시나리오                        | ExceptionMessage                | HTTP Status |
|-----------------------------|---------------------------------|-------------|
| PDF 다운로드 실패                 | `AI_SERVER_COMMUNICATION_ERROR` | 500         |
| Initiate 비정상 응답 / 세션 URL 없음 | `AI_SERVER_RESPONSE_ERROR`      | 500         |
| 파일 상태 `FAILED`              | `AI_SERVER_RESPONSE_ERROR`      | 500         |
| 폴링 타임아웃 (60초)               | `AI_SERVER_TIMEOUT`             | 504         |
| Thread interrupted          | `AI_SERVER_COMMUNICATION_ERROR` | 500         |
| deleteFile 실패               | warn 로그만 (예외 전파 X)              | —           |

---

## 참고 링크

- [Gemini File API 문서](https://ai.google.dev/gemini-api/docs/files)
- [Gemini File API REST Reference](https://ai.google.dev/api/files)
- [Spring 6 RestClient](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)
