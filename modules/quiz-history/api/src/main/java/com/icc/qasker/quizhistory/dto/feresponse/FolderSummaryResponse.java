package com.icc.qasker.quizhistory.dto.feresponse;

/** 폴더 목록 항목. count는 해당 폴더에 속한 기록 수. */
public record FolderSummaryResponse(String folderId, String name, long count) {}
