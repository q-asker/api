package com.icc.qasker.quiz.service.upload;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.document.ConvertService;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.oci.FileValidateService;
import com.icc.qasker.oci.ObjectStorageService;
import com.icc.qasker.quiz.dto.feresponse.FileUploadResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

  private final ObjectStorageService objectStorageService;
  private final GeminiFileService geminiFileService;
  private final FileValidateService fileValidateService;
  private final ConvertService convertService;
  private final MeterRegistry registry;

  @PostConstruct
  void eagerRegisterMetrics() {
    for (String type : new String[] {"pdf", "pptx", "ppt", "docx", "doc"}) {
      Counter.builder("file.upload.request")
          .description("파일 업로드 요청 수 (확장자별)")
          .tag("file_type", type)
          .register(registry);
    }
  }

  /** 파일(PDF, PPT, DOCX)을 PDF로 변환 후 OCI와 Gemini에 동시 업로드한다. */
  public FileUploadResponse upload(MultipartFile file) {
    String originalFileName = file.getOriginalFilename();

    fileValidateService.validateFileWithThrowing(
        originalFileName, file.getSize(), file.getContentType());

    Path tempFile = null;
    Path pdfFile = null;
    try {
      // 요청 파일 타입 카운팅
      String extension = getExtensionOf(originalFileName);
      Counter.builder("file.upload.request")
          .description("파일 업로드 요청 수 (확장자별)")
          .tag("file_type", extension.substring(1).toLowerCase())
          .register(registry)
          .increment();

      // 1. 임시 파일로 저장
      tempFile = Files.createTempFile(UUID.randomUUID().toString(), extension);
      file.transferTo(tempFile.toFile());

      // 2. PDF 변환 (이미 PDF이면 그대로 반환)
      String fileName = tempFile.getFileName().toString();
      if (fileName.toLowerCase().endsWith(".pdf")) {
        pdfFile = tempFile;
      } else {
        pdfFile = convertService.convertToPdf(tempFile);
      }

      // 이펙티블리 파이널
      final Path finalPdfFile = pdfFile;

      // 3. Gemini용 임시 파일 복사 (백그라운드 업로드가 끝날 때까지 유지)
      Path geminiCopy =
          Files.copy(
              finalPdfFile,
              Files.createTempFile("gemini-upload-", ".pdf"),
              StandardCopyOption.REPLACE_EXISTING);

      // 4. OCI + Gemini 동시 시작
      CompletableFuture<String> ociFuture =
          CompletableFuture.supplyAsync(
              () -> objectStorageService.uploadPdf(finalPdfFile, originalFileName));

      // Gemini 업로드: 완료 시 geminiCopy 정리, 예외는 보존 (캐시에서 join 시 처리)
      CompletableFuture<FileMetadata> geminiFuture =
          CompletableFuture.supplyAsync(() -> geminiFileService.uploadPdfFromFile(geminiCopy))
              .whenComplete(
                  (metadata, ex) -> {
                    deleteQuietly(geminiCopy);
                    if (ex != null) {
                      log.warn("Gemini 백그라운드 업로드 실패 (퀴즈 생성 시 재시도): {}", ex.getMessage());
                    } else {
                      log.info("Gemini 백그라운드 업로드 완료: name={}", metadata.name());
                    }
                  });

      // OCI 업로드는 필수 — 실패 시 예외 발생
      String cdnUrl = ociFuture.join();

      // Gemini Future를 캐시에 즉시 저장 — 퀴즈 생성 시 awaitCachedFileMetadata()로 대기/조회
      geminiFileService.cacheUploadFuture(cdnUrl, geminiFuture);

      log.info("OCI 업로드 완료, Gemini는 백그라운드 처리 중: {}", cdnUrl);
      return new FileUploadResponse(cdnUrl);
    } catch (Exception e) {
      throw new CustomException(
          ExceptionMessage.DEFAULT_ERROR, "파일 업로드 실패: " + originalFileName, e);
    } finally {
      deleteQuietly(tempFile);
      // pdfFile이 tempFile과 다른 경우에만 삭제 (변환이 발생한 경우)
      if (pdfFile != null && !pdfFile.equals(tempFile)) {
        deleteQuietly(pdfFile);
      }
    }
  }

  private String getExtensionOf(String fileName) {
    if (fileName == null) {
      throw new CustomException(ExceptionMessage.EXTENSION_NOT_EXIST);
    }
    int lastDotIndex = fileName.lastIndexOf(".");
    if (lastDotIndex == -1) {
      throw new CustomException(ExceptionMessage.EXTENSION_NOT_EXIST);
    }
    return fileName.substring(lastDotIndex);
  }

  private void deleteQuietly(Path path) {
    if (path != null) {
      try {
        Files.deleteIfExists(path);
      } catch (Exception e) {
        log.warn("임시 파일 삭제 실패: {}", path, e);
      }
    }
  }
}
