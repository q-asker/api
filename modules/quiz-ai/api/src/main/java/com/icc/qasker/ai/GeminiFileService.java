package com.icc.qasker.ai;

import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface GeminiFileService {

  /** CDN URL에서 PDF를 다운로드하고, 지정된 페이지가 있다면 해당 페이지만 추출하여 Gemini에 업로드한다. */
  FileMetadata uploadPdf(String pdfUrl, List<Integer> pages);

  /** 로컬 파일을 직접 Gemini에 업로드한다. */
  FileMetadata uploadPdfFromFile(Path pdfFile);

  void deleteFile(String fileName);

  FileMetadata waitForProcessing(String fileName) throws InterruptedException;

  /** URL과 페이지 범위를 조합하여 고유한 캐시 키를 생성한다. */
  String generateCacheKey(String url, List<Integer> pages);

  /** 진행 중인 Gemini 업로드 Future를 캐시에 저장한다. */
  void cacheUploadFuture(String cacheKey, CompletableFuture<FileMetadata> future);

  /** 캐시에서 Gemini 파일 메타데이터를 조회한다. cacheKey는 url 또는 url+pages 조합일 수 있다. */
  Optional<FileMetadata> awaitCachedFileMetadata(String cacheKey);
}
