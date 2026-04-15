package com.icc.qasker.document.service;

import com.icc.qasker.document.ConvertService;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
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
  private final MeterRegistry registry;

  @PostConstruct
  void eagerRegisterMetrics() {
    for (String ext : new String[] {"pptx", "ppt", "docx", "doc"}) {
      for (String result : new String[] {"success", "fail"}) {
        Timer.builder("document.conversion.duration")
            .publishPercentileHistogram(true)
            .serviceLevelObjectives(
                Duration.ofSeconds(1),
                Duration.ofSeconds(3),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30))
            .tag("source_type", ext)
            .tag("result", result)
            .description("문서 변환(PPT/DOCX → PDF) 소요 시간")
            .register(registry);
      }
    }
  }

  @Override
  public Path convertToPdf(Path inputFile) {
    String fileName = inputFile.getFileName().toString();

    String extension = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();

    // 이미 PDF이면 변환 없이 그대로 반환
    if (".pdf".equals(extension)) {
      return inputFile;
    }

    // 지원하지 않는 확장자 검증
    if (!SUPPORTED_EXTENSIONS.contains(extension)) {
      throw new CustomException(ExceptionMessage.UNSUPPORTED_FILE_TYPE);
    }

    String pdfFileName = fileName.substring(0, fileName.lastIndexOf(".")) + ".pdf";
    File pdfFile = new File(TEMP_DIR, pdfFileName);

    Timer.Sample sample = Timer.start(registry);
    try {
      log.info("PDF 변환 시작: {}", inputFile);
      documentConverter.convert(inputFile.toFile()).to(pdfFile).execute();
      log.info("PDF 변환 완료: {}", pdfFile.getAbsolutePath());

      // 문서 변환 Percentile Histogram Timer — tail latency SLO 설정용
      sample.stop(
          Timer.builder("document.conversion.duration")
              .publishPercentileHistogram(true)
              .serviceLevelObjectives(
                  Duration.ofSeconds(1),
                  Duration.ofSeconds(3),
                  Duration.ofSeconds(5),
                  Duration.ofSeconds(10),
                  Duration.ofSeconds(30))
              .tag("source_type", extension.substring(1))
              .tag("result", "success")
              .description("문서 변환(PPT/DOCX → PDF) 소요 시간")
              .register(registry));
      return pdfFile.toPath();
    } catch (CustomException e) {
      sample.stop(
          Timer.builder("document.conversion.duration")
              .tag("source_type", extension.substring(1))
              .tag("result", "fail")
              .description("문서 변환(PPT/DOCX → PDF) 소요 시간")
              .register(registry));
      throw e;
    } catch (Exception e) {
      sample.stop(
          Timer.builder("document.conversion.duration")
              .tag("source_type", extension.substring(1))
              .tag("result", "fail")
              .description("문서 변환(PPT/DOCX → PDF) 소요 시간")
              .register(registry));
      throw new CustomException(ExceptionMessage.CONVERT_FAILED, "PDF 변환 중 오류: " + inputFile, e);
    }
  }
}
