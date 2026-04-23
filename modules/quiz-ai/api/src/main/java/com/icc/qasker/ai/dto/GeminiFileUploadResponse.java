package com.icc.qasker.ai.dto;

/** 파일 업로드 응답 매핑. */
public record GeminiFileUploadResponse(FileMetadata file) {

  /**
   * 파일 메타데이터.
   *
   * @param name GCS blob 경로 — 삭제 시 식별자
   * @param displayName 업로드 시 지정한 표시 이름
   * @param mimeType MIME 타입 (예: "application/pdf")
   * @param sizeBytes 파일 크기
   * @param createTime 생성 시각
   * @param updateTime 수정 시각
   * @param state 처리 상태
   * @param uri 파일 URI (예: "gs://bucket/path") — 캐시에서 fileUri로 참조
   */
  public record FileMetadata(
      String name,
      String displayName,
      String mimeType,
      String sizeBytes,
      String createTime,
      String updateTime,
      String state,
      String uri,
      java.util.List<Integer> sourcePages) {}
}
