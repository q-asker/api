package com.icc.qasker.quizhistory.dto.feresponse;

/** 폴더 생성 응답. folderId는 Hashids 인코딩 문자열. */
public record FolderResponse(String folderId, String name) {}
