package com.icc.qasker.ai.dto;

/**
 * Gemini File API 업로드/조회 응답 매핑. JSON 구조: { "file": { ... } }
 */
public record GeminiFileUploadResponse(
    FileMetadata file
) {

    /**
     * Gemini 파일 메타데이터.
     *
     * @param name        파일 리소스 이름 (예: "files/abc123") — 조회/삭제 시 식별자
     * @param displayName 업로드 시 지정한 표시 이름
     * @param mimeType    MIME 타입 (예: "application/pdf")
     * @param sizeBytes   파일 크기 (Gemini가 문자열로 반환)
     * @param createTime  생성 시각 (ISO 8601)
     * @param updateTime  수정 시각 (ISO 8601)
     * @param state       처리 상태: "PROCESSING" | "ACTIVE" | "FAILED"
     * @param uri         파일 URI — generateContent/Cache에서 fileUri로 참조
     */
    public record FileMetadata(
        String name,
        String displayName,
        String mimeType,
        String sizeBytes,
        String createTime,
        String updateTime,
        String state,
        String uri
    ) {

    }
}
