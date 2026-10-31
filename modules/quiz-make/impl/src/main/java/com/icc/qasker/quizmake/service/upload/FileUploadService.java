package com.icc.qasker.quizmake.service.upload;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.document.ConvertService;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.oci.FileValidateService;
import com.icc.qasker.quizmake.dto.feresponse.FileUploadResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

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

      // 3. GCS 업로드 — PDF 의 단일 저장소. CDN 이 같은 객체를 사용자에게, 생성 시 Gemini 가 gs:// 로 직접 읽는다
      String cdnUrl = geminiFileService.storePdf(pdfFile, originalFileName);
      return new FileUploadResponse(cdnUrl);
    } catch (Exception e) {
      if (e instanceof CustomException ce) {
        throw ce;
      }
      throw new CustomException(
          ExceptionMessage.DEFAULT_ERROR, "파일 업로드 실패: " + originalFileName, e);
    } finally {
      deleteQuietly(tempFile);
      deleteQuietly(pdfFile);
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
        log.warn("[파일 정리 실패] 임시 파일 삭제 실패 path={}", path, e);
      }
    }
  }
}
