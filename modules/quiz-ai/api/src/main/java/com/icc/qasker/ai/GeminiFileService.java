package com.icc.qasker.ai;

import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface GeminiFileService {

  /** CDN URL에서 PDF를 다운로드하여 Gemini에 업로드한다. */
  FileMetadata uploadPdf(String pdfUrl);

  /** 로컬 파일을 직접 Gemini에 업로드한다. */
  FileMetadata uploadPdfFromFile(Path pdfFile);

  void deleteFile(String fileName);

  FileMetadata waitForProcessing(String fileName) throws InterruptedException;

  /** 진행 중인 Gemini 업로드 Future를 캐시에 저장한다. */
  void cacheUploadFuture(String cdnUrl, CompletableFuture<FileMetadata> future);

  /** 캐시에서 Gemini 파일 메타데이터를 조회한다. 업로드가 진행 중이면 완료될 때까지 대기하고, 실패 시 캐시에서 제거하고 empty를 반환한다. */
  Optional<FileMetadata> awaitCachedFileMetadata(String cdnUrl);
}
