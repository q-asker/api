package com.icc.qasker.quizhistory.dto.feresponse;

import java.util.List;

/** 폴더 목록 응답. unclassifiedCount는 어느 폴더에도 속하지 않은 기록 수(미분류). */
public record FolderListResponse(List<FolderSummaryResponse> folders, long unclassifiedCount) {}
