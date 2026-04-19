package com.icc.qasker.quizhistory.dto.feresponse;

import java.util.List;

/** 히스토리 목록 페이지네이션 응답 */
public record HistoryPageResponse(
    List<HistorySummaryResponse> content,
    long totalCount,
    int totalPages,
    int currentPage,
    int size) {}
