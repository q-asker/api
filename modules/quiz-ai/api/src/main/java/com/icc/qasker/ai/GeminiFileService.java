package com.icc.qasker.ai;

import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import java.nio.file.Path;

public interface GeminiFileService {

  /** PDF 원본을 GCS 에 저장하고, 사용자에게 내줄 CDN URL 을 반환한다. GCS 가 PDF 의 단일 저장소다. */
  String storePdf(Path pdfFile, String originalFileName);

  /**
   * CDN URL 을 Gemini 가 읽을 gs:// 경로로 되돌린다(다운로드·재업로드 없음). 출제 페이지는 자르지 않고 프롬프트로 지정한다. 객체가 보관 기한이 지나
   * 삭제됐으면 UPLOADED_FILE_DELETED 를 던진다.
   */
  FileMetadata resolvePdf(String cdnUrl);

  FileMetadata waitForProcessing(String fileName) throws InterruptedException;
}
