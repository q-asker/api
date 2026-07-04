package com.icc.qasker.ai.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.icc.qasker.ai.util.PdfUtils.SlicedPdf;
import com.icc.qasker.oci.FileValidateService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 버전업 세이프티 넷 (RT-003): pdfbox 3.0.3 → 3.0.7 승격이 PdfUtils.extractPages()의 로드/페이지 카운트/save 계약을 깨뜨리지
 * 않음을 확인한다.
 */
class PdfUtilsTest {

  private PdfUtils pdfUtils;
  private Path sourcePdf;

  @BeforeEach
  void setUp() throws Exception {
    pdfUtils = new PdfUtils(mock(FileValidateService.class));
    sourcePdf = Files.createTempFile("pdfutils-test-source-", ".pdf");
    try (PDDocument doc = new PDDocument()) {
      doc.addPage(new PDPage());
      doc.addPage(new PDPage());
      doc.addPage(new PDPage());
      doc.save(sourcePdf.toFile());
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    Files.deleteIfExists(sourcePdf);
  }

  @Test
  void extractPages_returnsSlicedPdfWithRequestedPages() throws Exception {
    SlicedPdf sliced = pdfUtils.extractPages(sourcePdf, List.of(1, 3));

    assertThat(sliced.sourcePages()).containsExactly(1, 3);
    try (PDDocument out = Loader.loadPDF(sliced.path().toFile())) {
      assertThat(out.getNumberOfPages()).isEqualTo(2);
    }
    Files.deleteIfExists(sliced.path());
  }

  @Test
  void extractPages_ignoresOutOfRangePages() throws Exception {
    SlicedPdf sliced = pdfUtils.extractPages(sourcePdf, List.of(1, 99));

    assertThat(sliced.sourcePages()).containsExactly(1);
    try (PDDocument out = Loader.loadPDF(sliced.path().toFile())) {
      assertThat(out.getNumberOfPages()).isEqualTo(1);
    }
    Files.deleteIfExists(sliced.path());
  }

  @Test
  void extractPages_emptyPagesReturnsSourceUnchanged() throws Exception {
    SlicedPdf sliced = pdfUtils.extractPages(sourcePdf, List.of());

    assertThat(sliced.path()).isEqualTo(sourcePdf);
    assertThat(sliced.sourcePages()).isEmpty();
  }
}
