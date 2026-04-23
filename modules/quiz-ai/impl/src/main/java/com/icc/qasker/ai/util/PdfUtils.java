package com.icc.qasker.ai.util;

import com.icc.qasker.oci.FileValidateService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfUtils {

  private final FileValidateService fileValidateService;

  public Path downloadToTemp(String pdfUrl) throws IOException {
    fileValidateService.checkCdnUrlWithThrowing(pdfUrl);
    Path tempFile = Files.createTempFile("gemini-download-", ".pdf");

    log.debug("PDF 다운로드 시작: {} -> {}", pdfUrl, tempFile);

    try (InputStream in = URI.create(pdfUrl).toURL().openStream()) {
      Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      deleteTempFile(tempFile);
      throw e;
    }

    log.debug("PDF 다운로드 완료: {} bytes", Files.size(tempFile));
    return tempFile;
  }

  /**
   * 원본 PDF에서 지정된 페이지들만 추출하여 새로운 임시 PDF 파일을 생성한다.
   *
   * @param sourcePdf 원본 PDF 경로
   * @param pages 추출할 페이지 번호 리스트 (1-based)
   * @return 추출된 파일 경로와 실제 포함된 원본 페이지 번호 리스트를 담은 객체
   */
  public SlicedPdf extractPages(Path sourcePdf, List<Integer> pages) throws IOException {
    if (pages == null || pages.isEmpty()) {
      return new SlicedPdf(sourcePdf, List.of());
    }

    log.debug("PDF 페이지 추출 시작: {} pages from {}", pages.size(), sourcePdf);
    List<Integer> successPages = new java.util.ArrayList<>();

    try (PDDocument sourceDoc = Loader.loadPDF(sourcePdf.toFile())) {
      try (PDDocument targetDoc = new PDDocument()) {
        int totalPages = sourceDoc.getNumberOfPages();
        for (int pageNum : pages) {
          if (pageNum > 0 && pageNum <= totalPages) {
            targetDoc.addPage(sourceDoc.getPage(pageNum - 1));
            successPages.add(pageNum);
          } else {
            log.warn("유효하지 않은 페이지 번호 무시: {} (총 페이지: {})", pageNum, totalPages);
          }
        }

        if (targetDoc.getNumberOfPages() == 0) {
          log.warn("추출된 페이지가 없어 원본을 그대로 반환합니다.");
          return new SlicedPdf(sourcePdf, List.of());
        }

        Path slicedFile = Files.createTempFile("gemini-sliced-", ".pdf");
        targetDoc.save(slicedFile.toFile());
        log.debug("PDF 페이지 추출 완료: {} -> {}", sourcePdf, slicedFile);
        return new SlicedPdf(slicedFile, successPages);
      }
    }
  }

  public record SlicedPdf(Path path, List<Integer> sourcePages) {}

  public void deleteTempFile(Path tempFile) {
    if (tempFile == null) {
      return;
    }
    try {
      boolean deleted = Files.deleteIfExists(tempFile);
      if (deleted) {
        log.debug("임시 파일 삭제 완료: {}", tempFile);
      }
    } catch (IOException e) {
      log.warn("임시파일 삭제 실패: {} - {}", tempFile, e.getMessage());
    }
  }
}
