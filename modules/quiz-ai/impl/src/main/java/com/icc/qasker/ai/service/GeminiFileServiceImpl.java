package com.icc.qasker.ai.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.util.PdfUtils;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class GeminiFileServiceImpl implements GeminiFileService {

  private static final int POLL_INTERVAL_MS = 1_000;
  private static final int MAX_POLL_ATTEMPTS = 30;
  private static final String STATE_ACTIVE = "ACTIVE";
  private static final String STATE_FAILED = "FAILED";

  private final RestClient restClient;
  private final String apiKey;
  private final PdfUtils pdfUtils;
  private final MeterRegistry registry;
  private final Timer uploadTimer;
  private final Counter fileRequestNew;
  private final Counter fileRequestRepeat;

  // Gemini 파일 업로드 Future 캐시 (CloudFront URL → CompletableFuture<FileMetadata>)
  // TTL 47시간: Gemini Files API 48시간 자동 삭제 전 안전 마진
  // 업로드 진행 중인 Future도 저장되므로 중복 업로드를 방지한다
  private final Cache<String, CompletableFuture<FileMetadata>> uploadFutureCache =
      Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(Duration.ofHours(47)).build();

  // 같은 파일 URL로 /generation이 재요청되었는지 추적하는 seen-set
  private final ConcurrentHashMap.KeySetView<String, Boolean> seenFileUrls =
      ConcurrentHashMap.newKeySet();

  public GeminiFileServiceImpl(
      @Qualifier("geminiFileRestClient") RestClient restClient,
      GoogleGenAiConnectionProperties properties,
      PdfUtils pdfUtils,
      MeterRegistry registry) {
    this.restClient = restClient;
    this.apiKey = properties.getApiKey();
    this.pdfUtils = pdfUtils;
    this.registry = registry;

    this.uploadTimer =
        Timer.builder("file.upload.gemini.duration")
            .description("Gemini 파일 업로드 소요 시간")
            .register(registry);
    this.fileRequestNew =
        Counter.builder("gemini.file.request")
            .tag("type", "new")
            .description("새로운 파일로 퀴즈 생성 요청 수")
            .register(registry);
    this.fileRequestRepeat =
        Counter.builder("gemini.file.request")
            .tag("type", "repeat")
            .description("같은 파일로 퀴즈 재생성 요청 수")
            .register(registry);
  }

  @Override
  public FileMetadata uploadPdf(String pdfUrl) {
    Path tempFile = null;

    try {
      tempFile = pdfUtils.downloadToTemp(pdfUrl);
      FileMetadata metadata = doUpload(tempFile, extractFileName(pdfUrl));

      // 완료된 Future로 캐시에 저장
      uploadFutureCache.put(pdfUrl, CompletableFuture.completedFuture(metadata));
      return metadata;
    } catch (IOException e) {
      throw new CustomException(
          ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR, "PDF 업로드 중 I/O 오류", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CustomException(
          ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR, "PDF 처리 대기 중 인터럽트 발생", e);
    } finally {
      pdfUtils.deleteTempFile(tempFile);
    }
  }

  @Override
  public FileMetadata uploadPdfFromFile(Path pdfFile) {
    try {
      return doUpload(pdfFile, pdfFile.getFileName().toString());
    } catch (IOException e) {
      throw new CustomException(
          ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR, "PDF 업로드 중 I/O 오류", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CustomException(
          ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR, "PDF 처리 대기 중 인터럽트 발생", e);
    }
  }

  @Override
  public void cacheUploadFuture(String cloudFrontUrl, CompletableFuture<FileMetadata> future) {
    uploadFutureCache.put(cloudFrontUrl, future);
    log.info("Gemini 업로드 Future 캐시 저장: url={}", cloudFrontUrl);
  }

  @Override
  public Optional<FileMetadata> awaitCachedFileMetadata(String cloudFrontUrl) {
    // 같은 파일 URL 재요청 여부 추적
    boolean isNew = seenFileUrls.add(cloudFrontUrl);
    if (isNew) {
      fileRequestNew.increment();
    } else {
      fileRequestRepeat.increment();
    }

    CompletableFuture<FileMetadata> future = uploadFutureCache.getIfPresent(cloudFrontUrl);
    if (future == null) {
      return Optional.empty();
    }

    try {
      FileMetadata metadata = future.join();
      log.info("Gemini 파일 캐시 히트: url={}, name={}", cloudFrontUrl, metadata.name());
      return Optional.of(metadata);
    } catch (CompletionException e) {
      uploadFutureCache.invalidate(cloudFrontUrl);
      log.warn("캐시된 Gemini 업로드 실패, 캐시 제거: url={}, error={}", cloudFrontUrl, e.getMessage());
      return Optional.empty();
    }
  }

  private FileMetadata doUpload(Path pdfFile, String displayName)
      throws IOException, InterruptedException {
    Timer.Sample sample = Timer.start();
    long fileSize = Files.size(pdfFile);

    String uploadSessionUrl = initiateUpload(fileSize, displayName);

    GeminiFileUploadResponse response = uploadBytes(uploadSessionUrl, pdfFile, fileSize);

    String fileName = response.file().name();

    log.info("PDF 업로드 완료: name={}, state={}", fileName, response.file().state());

    FileMetadata metadata = waitForProcessing(fileName);
    sample.stop(uploadTimer);
    log.info("파일 처리 완료: name={}, uri={}", metadata.name(), metadata.uri());
    return metadata;
  }

  @Override
  public void deleteFile(String fileName) {
    try {
      restClient
          .delete()
          .uri("/v1beta/" + fileName + "?key={key}", apiKey)
          .retrieve()
          .toBodilessEntity();

      log.info("Gemini 파일 삭제 완료: name={}", fileName);
    } catch (Exception e) {
      log.warn("Gemini 파일 삭제 실패 (무시): name={}, error={}", fileName, e.getMessage());
    }
  }

  private String initiateUpload(long fileSize, String displayName) {
    Map<String, Map<String, String>> requestBody =
        Map.of("file", Map.of("display_name", displayName));

    // exchange()를 사용하는 이유: 응답 헤더에서 업로드 세션 URL을 추출해야 하기 때문
    // retrieve()는 body만 반환하므로 헤더 접근 불가
    return restClient
        .post()
        .uri("/upload/v1beta/files?key={key}", apiKey)
        .header("X-Goog-Upload-Protocol", "resumable")
        .header("X-Goog-Upload-Command", "start")
        .header("X-Goog-Upload-Header-Content-Type", "application/pdf")
        .header("X-Goog-Upload-Header-Content-Length", String.valueOf(fileSize))
        .contentType(MediaType.APPLICATION_JSON)
        .exchange(
            (req, res) -> {
              if (!res.getStatusCode().is2xxSuccessful()) {
                throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
              }
              String url = res.getHeaders().getFirst("x-goog-upload-url");
              if (url == null || url.isBlank()) {
                throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
              }
              return url;
            });
  }

  private GeminiFileUploadResponse uploadBytes(
      String uploadSessionUrl, Path pdfFile, long fileSize) {
    return restClient
        .post()
        .uri(URI.create(uploadSessionUrl))
        .header("X-Goog-Upload-Command", "upload, finalize")
        .header("X-Goog-Upload-Offset", "0")
        .header("Content-Length", String.valueOf(fileSize))
        .body(new FileSystemResource(pdfFile))
        .retrieve()
        .body(GeminiFileUploadResponse.class);
  }

  @Override
  public FileMetadata waitForProcessing(String fileName) throws InterruptedException {
    for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
      FileMetadata metadata = getFile(fileName);
      String state = metadata.state();

      log.debug("파일 상태 폴링 [{}/{}]: name={}, state={}", attempt, MAX_POLL_ATTEMPTS, fileName, state);

      if (STATE_ACTIVE.equals(state)) {
        return metadata;
      }
      if (STATE_FAILED.equals(state)) {
        throw new CustomException(
            ExceptionMessage.AI_SERVER_RESPONSE_ERROR, "Gemini 파일 처리 실패: name=" + fileName);
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }

    throw new CustomException(
        ExceptionMessage.AI_SERVER_TIMEOUT,
        "파일 처리 타임아웃: name=%s, %dms * %d attempts"
            .formatted(fileName, POLL_INTERVAL_MS, MAX_POLL_ATTEMPTS));
  }

  private FileMetadata getFile(String fileName) {
    return restClient
        .get()
        .uri("/v1beta/" + fileName + "?key={key}", apiKey)
        .retrieve()
        .body(FileMetadata.class);
  }

  private String extractFileName(String url) {
    int lastSlash = url.lastIndexOf('/');
    if (lastSlash >= 0 && lastSlash < url.length() - 1) {
      String name = url.substring(lastSlash + 1);
      int queryIdx = name.indexOf('?');
      return queryIdx > 0 ? name.substring(0, queryIdx) : name;
    }
    throw new IllegalArgumentException("파일 이름을 찾을수 없음 url: " + url);
  }
}
