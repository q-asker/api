package com.icc.qasker.ai;

import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import java.nio.file.Path;
import java.util.Optional;

public interface GeminiFileService {

  /** CloudFront URL에서 PDF를 다운로드하여 Gemini에 업로드한다. */
  FileMetadata uploadPdf(String pdfUrl);

  /** 로컬 파일을 직접 Gemini에 업로드한다. */
  FileMetadata uploadPdfFromFile(Path pdfFile);

  void deleteFile(String fileName);

  FileMetadata waitForProcessing(String fileName) throws InterruptedException;

  /** Gemini 파일 메타데이터를 캐시에 저장한다. */
  void cacheFileMetadata(String cloudFrontUrl, FileMetadata metadata);

  /** 캐시에서 Gemini 파일 메타데이터를 조회한다. */
  Optional<FileMetadata> getCachedFileMetadata(String cloudFrontUrl);
}
