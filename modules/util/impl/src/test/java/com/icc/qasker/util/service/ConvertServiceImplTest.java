package com.icc.qasker.util.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.job.ConversionJobWithOptionalSourceFormatUnspecified;
import org.jodconverter.core.job.ConversionJobWithOptionalTargetFormatUnspecified;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConvertServiceImplTest {

  // JODConverter의 DocumentConverter를 모킹하여 LibreOffice 없이 테스트한다
  private DocumentConverter documentConverter;
  // 테스트 대상: ConvertServiceImpl (DocumentConverter를 주입받는 구현체)
  private ConvertServiceImpl convertService;

  @BeforeEach
  void setUp() {
    // DocumentConverter의 가짜 객체를 생성한다 (실제 LibreOffice 프로세스를 띄우지 않음)
    documentConverter = mock(DocumentConverter.class);
    // 모킹된 DocumentConverter를 주입하여 테스트 대상 서비스를 생성한다
    convertService = new ConvertServiceImpl(documentConverter, new SimpleMeterRegistry());
  }

  // 클래스패스(src/test/resources)에서 테스트 파일의 절대 경로를 가져오는 헬퍼
  private Path resourcePath(String name) {
    URL url = getClass().getClassLoader().getResource("testfiles/" + name);
    return Paths.get(url.getPath());
  }

  // 보장: PDF 파일이 불필요하게 LibreOffice를 거치지 않아 성능 낭비와 변환 오류를 방지한다
  @Test
  @DisplayName("PDF 파일이면 변환 없이 그대로 반환한다")
  void convertToPdf_alreadyPdf_returnsInputFile() {
    // Given: 테스트 리소스에서 sample.pdf 경로를 준비한다
    Path pdfFile = resourcePath("sample.pdf");

    // When: convertToPdf를 호출한다
    Path result = convertService.convertToPdf(pdfFile);

    // Then: 입력 경로 그대로 반환되어야 한다
    assertThat(result).isEqualTo(pdfFile);
    // Then: DocumentConverter.convert()가 한 번도 호출되지 않았음을 검증한다
    verify(documentConverter, never()).convert(any(File.class));
  }

  // 보장: .txt, .xlsx 등 비지원 파일이 LibreOffice에 전달되지 않아 예측 불가 오류를 사전 차단한다
  @Test
  @DisplayName("지원하지 않는 확장자면 UNSUPPORTED_FILE_TYPE 예외를 던진다")
  void convertToPdf_unsupportedExtension_throwsException() {
    // Given: 지원하지 않는 .txt 파일 경로를 준비한다
    Path txtFile = resourcePath("sample.txt");

    // When & Then: convertToPdf 호출 시 CustomException이 발생하고,
    // 메시지가 UNSUPPORTED_FILE_TYPE의 메시지와 일치하는지 검증한다
    assertThatThrownBy(() -> convertService.convertToPdf(txtFile))
        .isInstanceOf(CustomException.class)
        .hasMessage(ExceptionMessage.UNSUPPORTED_FILE_TYPE.getMessage());
  }

  // 보장: PPTX → PDF 변환 파이프라인(convert → to → execute)이 정상 연결되어 사용자가 PPT를 업로드할 수 있다
  @Test
  @DisplayName("PPTX 파일을 PDF로 변환한다")
  void convertToPdf_pptxFile_convertsToPdf() throws Exception {
    // Given: 테스트 리소스에서 sample.pptx 경로를 준비한다
    Path pptxFile = resourcePath("sample.pptx");

    // JODConverter API는 빌더 패턴 체이닝으로 동작한다:
    //   documentConverter.convert(source) → sourceJob
    //   sourceJob.to(target)              → targetJob
    //   targetJob.execute()               → 실제 변환 실행
    // 각 단계를 모킹하여 체이닝이 정상 연결되는지 검증한다

    // sourceJob: convert() 호출 결과를 나타내는 중간 객체
    ConversionJobWithOptionalSourceFormatUnspecified sourceJob =
        mock(ConversionJobWithOptionalSourceFormatUnspecified.class);
    // targetJob: to() 호출 결과를 나타내는 최종 객체 (execute()를 가짐)
    ConversionJobWithOptionalTargetFormatUnspecified targetJob =
        mock(ConversionJobWithOptionalTargetFormatUnspecified.class);

    // convert(아무 File)이 호출되면 sourceJob을 반환하도록 설정
    when(documentConverter.convert(any(File.class))).thenReturn(sourceJob);
    // sourceJob.to(아무 File)이 호출되면 targetJob을 반환하도록 설정
    when(sourceJob.to(any(File.class))).thenReturn(targetJob);

    // When: PPTX 파일에 대해 변환을 실행한다
    Path result = convertService.convertToPdf(pptxFile);

    // Then: 결과 파일명이 확장자만 .pdf로 바뀌어야 한다 (sample.pptx → sample.pdf)
    assertThat(result.getFileName().toString()).isEqualTo("sample.pdf");
    // Then: convert()에 원본 PPTX 파일이 전달되었는지 검증한다
    verify(documentConverter).convert(pptxFile.toFile());
    // Then: 최종적으로 execute()가 호출되어 실제 변환이 트리거되었는지 검증한다
    verify(targetJob).execute();
  }

  // 보장: DOCX → PDF 변환이 PPTX와 동일한 경로로 동작하여 Word 문서 업로드를 지원한다
  @Test
  @DisplayName("DOCX 파일을 PDF로 변환한다")
  void convertToPdf_docxFile_convertsToPdf() throws Exception {
    // Given: 테스트 리소스에서 sample.docx 경로를 준비한다
    Path docxFile = resourcePath("sample.docx");

    // PPTX 테스트와 동일한 체이닝 모킹 — 파일 형식에 관계없이 같은 파이프라인을 탄다
    ConversionJobWithOptionalSourceFormatUnspecified sourceJob =
        mock(ConversionJobWithOptionalSourceFormatUnspecified.class);
    ConversionJobWithOptionalTargetFormatUnspecified targetJob =
        mock(ConversionJobWithOptionalTargetFormatUnspecified.class);

    when(documentConverter.convert(any(File.class))).thenReturn(sourceJob);
    when(sourceJob.to(any(File.class))).thenReturn(targetJob);

    // When: DOCX 파일에 대해 변환을 실행한다
    Path result = convertService.convertToPdf(docxFile);

    // Then: 결과 파일명이 .pdf로 바뀌어야 한다 (sample.docx → sample.pdf)
    assertThat(result.getFileName().toString()).isEqualTo("sample.pdf");
    // Then: convert()에 원본 DOCX 파일이 전달되었는지 검증한다
    verify(documentConverter).convert(docxFile.toFile());
    // Then: execute()가 호출되었는지 검증한다
    verify(targetJob).execute();
  }

  // 보장: LibreOffice 장애 시 원본 예외가 CustomException으로 래핑되어 클라이언트에 일관된 에러 응답을 반환한다
  @Test
  @DisplayName("변환 중 예외 발생 시 CONVERT_FAILED 예외를 던진다")
  void convertToPdf_conversionFails_throwsConvertFailed() {
    // Given: 테스트 리소스에서 sample.pptx 경로를 준비한다
    Path pptxFile = resourcePath("sample.pptx");

    // convert() 호출 시 RuntimeException을 던지도록 설정한다
    // (실제로는 LibreOffice 프로세스 오류, 타임아웃 등이 원인)
    when(documentConverter.convert(any(File.class))).thenThrow(new RuntimeException("변환 실패"));

    // When & Then: 내부 RuntimeException이 CustomException(CONVERT_FAILED)으로 감싸져서 던져지는지 검증한다
    // 클라이언트는 항상 동일한 형태의 에러 응답을 받게 된다
    assertThatThrownBy(() -> convertService.convertToPdf(pptxFile))
        .isInstanceOf(CustomException.class)
        .hasMessage(ExceptionMessage.CONVERT_FAILED.getMessage());
  }
}
