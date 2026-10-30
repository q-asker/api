package com.icc.qasker.ai.support;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.oci.FileValidateService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

/**
 * PDF 원본의 단일 저장소(GCS). 업로드 시 GCS 에 저장하고 CDN URL({@code cdn.file-base-url}/객체명)을 돌려준다 —
 * CDN(Cloudflare Worker files-hook)이 같은 객체를 GCS 에서 읽어 사용자에게 내준다. 생성 시에는 그 CDN URL 을 gs:// 경로로 되돌려
 * Gemini 에 그대로 넘기므로 다운로드·재업로드가 없다.
 */
@Slf4j
@Service
public class GeminiFileServiceImpl implements GeminiFileService {

  private final Storage storage;
  private final String bucketName;
  private final String fileBaseUrl;
  private final FileValidateService fileValidateService;
  private final Timer uploadTimer;
  private final Counter fileRequestNew;
  private final Counter fileRequestRepeat;

  private final ConcurrentHashMap.KeySetView<String, Boolean> seenFileUrls =
      ConcurrentHashMap.newKeySet();

  public GeminiFileServiceImpl(
      Storage storage,
      QAskerAiProperties aiProperties,
      @Value("${cdn.file-base-url}") String fileBaseUrl,
      FileValidateService fileValidateService,
      MeterRegistry registry) {
    this.storage = storage;
    this.bucketName = aiProperties.getGcs().getBucketName();
    this.fileBaseUrl = fileBaseUrl;
    this.fileValidateService = fileValidateService;

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
  public String storePdf(Path pdfFile, String originalFileName) {
    Timer.Sample sample = Timer.start();
    String objectName = UUID.randomUUID() + ".pdf";

    BlobInfo blobInfo =
        BlobInfo.newBuilder(bucketName, objectName)
            .setContentType("application/pdf")
            .setMetadata(
                Map.of(
                    "original-filename", UriUtils.encode(originalFileName, StandardCharsets.UTF_8)))
            .build();
    try {
      storage.createFrom(blobInfo, pdfFile);
      log.info(
          "GCS PDF 업로드 완료: bucket={}, blob={}, size={}bytes",
          bucketName,
          objectName,
          Files.size(pdfFile));
    } catch (IOException e) {
      throw new CustomException(ExceptionMessage.FILE_UPLOAD_FAILED, "GCS PDF 업로드 실패", e);
    } finally {
      sample.stop(uploadTimer);
    }
    return fileBaseUrl + "/" + objectName;
  }

  @Override
  public FileMetadata resolvePdf(String cdnUrl) {
    fileValidateService.checkCdnUrlWithThrowing(cdnUrl);
    String prefix = fileBaseUrl + "/";
    if (!cdnUrl.startsWith(prefix)) {
      throw new CustomException(ExceptionMessage.INVALID_URL_REQUEST, "pdfUrl=" + cdnUrl);
    }
    String objectName = stripQuery(cdnUrl.substring(prefix.length()));

    if (seenFileUrls.add(cdnUrl)) {
      fileRequestNew.increment();
    } else {
      fileRequestRepeat.increment();
    }

    // 객체가 없으면 보관 기한(GCS 수명 주기)이 지나 삭제된 것 — Gemini 호출 전에 확정해 사용자에게 사유를 알린다
    Blob blob = storage.get(BlobId.of(bucketName, objectName));
    if (blob == null) {
      throw new CustomException(ExceptionMessage.UPLOADED_FILE_DELETED, "pdfUrl=" + cdnUrl);
    }

    return new FileMetadata(
        objectName,
        objectName,
        "application/pdf",
        String.valueOf(blob.getSize()),
        null,
        null,
        "ACTIVE",
        "gs://" + bucketName + "/" + objectName,
        null);
  }

  @Override
  public FileMetadata waitForProcessing(String fileName) {
    return new FileMetadata(
        fileName,
        null,
        "application/pdf",
        null,
        null,
        null,
        "ACTIVE",
        "gs://" + bucketName + "/" + fileName,
        null);
  }

  private static String stripQuery(String path) {
    int queryIdx = path.indexOf('?');
    return queryIdx >= 0 ? path.substring(0, queryIdx) : path;
  }
}
