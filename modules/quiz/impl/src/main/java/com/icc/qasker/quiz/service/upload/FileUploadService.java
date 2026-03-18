package com.icc.qasker.quiz.service.upload;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.aws.S3Service;
import com.icc.qasker.aws.S3ValidateService;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.feresponse.FileUploadResponse;
import com.icc.qasker.util.ConvertService;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private final S3Service s3Service;
  private final GeminiFileService geminiFileService;
  private final S3ValidateService s3ValidateService;
  private final ConvertService convertService;

  /** 파일(PDF, PPT, DOCX)을 PDF로 변환 후 S3와 Gemini에 동시 업로드한다. */
  public FileUploadResponse upload(MultipartFile file) {
    String originalFileName = file.getOriginalFilename();
    s3ValidateService.validateFileWithThrowing(
        originalFileName, file.getSize(), file.getContentType());

    Path tempFile = null;
    Path pdfFile = null;
    try {
      // 1. 임시 파일로 저장
      String extension = getExtensionOf(originalFileName);
      tempFile = Files.createTempFile(UUID.randomUUID().toString(), extension);
      file.transferTo(tempFile.toFile());

      // 2. PDF 변환 (이미 PDF이면 그대로 반환)
      pdfFile = convertService.convertToPdf(tempFile);

      final Path finalPdfFile = pdfFile;

      // 3. S3 + Gemini 동시 업로드
      CompletableFuture<String> s3Future =
          CompletableFuture.supplyAsync(() -> s3Service.uploadPdf(finalPdfFile, originalFileName));

      CompletableFuture<FileMetadata> geminiFuture =
          CompletableFuture.supplyAsync(() -> geminiFileService.uploadPdfFromFile(finalPdfFile))
              .exceptionally(
                  ex -> {
                    log.warn("Gemini 사전 업로드 실패 (퀴즈 생성 시 재시도): {}", ex.getMessage());
                    return null;
                  });

      // S3 업로드는 필수 — 실패 시 예외 발생
      String cloudFrontUrl = s3Future.join();
      // Gemini 업로드는 best-effort: 실패 시 exceptionally에서 null을 반환하므로 예외가 전파되지 않는다.
      // null인 경우 퀴즈 생성 시점에 Gemini에 재업로드를 시도한다.
      FileMetadata metadata = geminiFuture.join();

      if (metadata != null) {
        geminiFileService.cacheFileMetadata(cloudFrontUrl, metadata);
        log.info("S3 + Gemini 동시 업로드 완료: {}", cloudFrontUrl);
      } else {
        log.info("S3 업로드 완료 (Gemini는 퀴즈 생성 시 재시도): {}", cloudFrontUrl);
      }

      return new FileUploadResponse(cloudFrontUrl);
    } catch (Exception e) {
      log.error("파일 업로드 실패: {}", originalFileName, e);
      throw new CustomException(ExceptionMessage.DEFAULT_ERROR);
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
