package com.icc.qasker.util.service;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.util.ConvertService;
import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jodconverter.core.DocumentConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * JODConverter를 이용하여 문서 파일을 PDF로 변환하는 구현체. JODConverter Spring Boot Starter가 LibreOffice 프로세스 풀을 자동
 * 관리한다. DocumentConverter 빈이 존재할 때만 등록된다 (test 프로파일에서는 no-op 구현체로 대체).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jodconverter.local", name = "enabled", havingValue = "true")
public class ConvertServiceImpl implements ConvertService {

  private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + File.separator;
  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".pptx", ".ppt", ".docx", ".doc");

  private final DocumentConverter documentConverter;

  @Override
  public Path convertToPdf(Path inputFile) {
    String fileName = inputFile.getFileName().toString();

    // 이미 PDF 파일이면 변환하지 않음
    if (fileName.toLowerCase().endsWith(".pdf")) {
      log.info("이미 PDF 파일이므로 변환을 건너뜁니다: {}", fileName);
      return inputFile;
    }

    // 지원하지 않는 확장자 검증
    String extension = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
    if (!SUPPORTED_EXTENSIONS.contains(extension)) {
      throw new CustomException(ExceptionMessage.UNSUPPORTED_FILE_TYPE);
    }

    String pdfFileName = fileName.substring(0, fileName.lastIndexOf(".")) + ".pdf";
    File pdfFile = new File(TEMP_DIR, pdfFileName);

    try {
      log.info("PDF 변환 시작: {}", inputFile);
      documentConverter.convert(inputFile.toFile()).to(pdfFile).execute();
      log.info("PDF 변환 완료: {}", pdfFile.getAbsolutePath());
      return pdfFile.toPath();
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      log.error("PDF 변환 중 오류 발생: {}", inputFile, e);
      throw new CustomException(ExceptionMessage.CONVERT_FAILED);
    }
  }
}
