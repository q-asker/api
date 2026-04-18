package com.icc.qasker.ai.service.gemini;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.util.PdfUtils;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GeminiFileServiceImpl implements GeminiFileService {

  private final Storage storage;
  private final String bucketName;
  private final PdfUtils pdfUtils;
  private final MeterRegistry registry;
  private final Timer uploadTimer;
  private final Counter fileRequestNew;
  private final Counter fileRequestRepeat;

  // GCS 업로드 Future 캐시 (CDN URL → CompletableFuture<FileMetadata>)
  // TTL 47시간: GCS 수명주기 정책(1일)과 정합
  private final Cache<String, CompletableFuture<FileMetadata>> uploadFutureCache =
      Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(Duration.ofHours(47)).build();

  // 같은 파일 URL로 /generation이 재요청되었는지 추적하는 seen-set
  private final ConcurrentHashMap.KeySetView<String, Boolean> seenFileUrls =
      ConcurrentHashMap.newKeySet();

  public GeminiFileServiceImpl(
      Storage storage, QAskerAiProperties aiProperties, PdfUtils pdfUtils, MeterRegistry registry) {
    this.storage = storage;
    this.bucketName = aiProperties.getGcs().getBucketName();
    this.pdfUtils = pdfUtils;
    this.registry = registry;

    this.uploadTimer =
        Timer.builder("file.upload.gcs.duration")
            .description("GCS 파일 업로드 소요 시간")
            .register(registry);
    this.fileRequestNew =
        Counter.builder("gcs.file.request")
            .tag("type", "new")
            .description("새로운 파일로 퀴즈 생성 요청 수")
            .register(registry);
    this.fileRequestRepeat =
        Counter.builder("gcs.file.request")
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

      uploadFutureCache.put(pdfUrl, CompletableFuture.completedFuture(metadata));
      return metadata;
    } catch (java.io.FileNotFoundException e) {
      throw new CustomException(
          ExceptionMessage.INVALID_URL_REQUEST, "PDF 파일을 찾을 수 없습니다: " + e.getMessage(), e);
    } catch (IOException e) {
      throw new CustomException(
          ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR, "PDF 업로드 중 I/O 오류", e);
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
    }
  }

  @Override
  public void cacheUploadFuture(String cdnUrl, CompletableFuture<FileMetadata> future) {
    uploadFutureCache.put(cdnUrl, future);
    log.info("GCS 업로드 Future 캐시 저장: url={}", cdnUrl);
  }

  @Override
  public Optional<FileMetadata> awaitCachedFileMetadata(String cdnUrl) {
    boolean isNew = seenFileUrls.add(cdnUrl);
    if (isNew) {
      fileRequestNew.increment();
    } else {
      fileRequestRepeat.increment();
    }

    CompletableFuture<FileMetadata> future = uploadFutureCache.getIfPresent(cdnUrl);
    if (future == null) {
      return Optional.empty();
    }

    try {
      FileMetadata metadata = future.join();
      log.info("GCS 파일 캐시 히트: url={}, name={}", cdnUrl, metadata.name());
      return Optional.of(metadata);
    } catch (CompletionException e) {
      uploadFutureCache.invalidate(cdnUrl);
      log.warn("캐시된 GCS 업로드 실패, 캐시 제거: url={}, error={}", cdnUrl, e.getMessage());
      return Optional.empty();
    }
  }

  private FileMetadata doUpload(Path pdfFile, String displayName) throws IOException {
    Timer.Sample sample = Timer.start();

    String blobName = UUID.randomUUID() + "/" + displayName;
    byte[] pdfBytes = Files.readAllBytes(pdfFile);

    BlobInfo blobInfo =
        BlobInfo.newBuilder(bucketName, blobName).setContentType("application/pdf").build();
    storage.create(blobInfo, pdfBytes);

    String gcsUri = "gs://" + bucketName + "/" + blobName;

    sample.stop(uploadTimer);
    log.info(
        "GCS PDF 업로드 완료: bucket={}, blob={}, size={}bytes", bucketName, blobName, pdfBytes.length);

    return new FileMetadata(
        blobName,
        displayName,
        "application/pdf",
        String.valueOf(pdfBytes.length),
        null,
        null,
        "ACTIVE",
        gcsUri);
  }

  @Override
  public void deleteFile(String fileName) {
    try {
      boolean deleted = storage.delete(BlobId.of(bucketName, fileName));
      if (deleted) {
        log.info("GCS 파일 삭제 완료: name={}", fileName);
      } else {
        log.warn("GCS 파일 삭제 대상 없음: name={}", fileName);
      }
    } catch (Exception e) {
      log.warn("GCS 파일 삭제 실패 (무시): name={}, error={}", fileName, e.getMessage());
    }
  }

  @Override
  public FileMetadata waitForProcessing(String fileName) throws InterruptedException {
    // GCS는 업로드 즉시 사용 가능하므로 폴링 불필요
    return new FileMetadata(
        fileName,
        null,
        "application/pdf",
        null,
        null,
        null,
        "ACTIVE",
        "gs://" + bucketName + "/" + fileName);
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
