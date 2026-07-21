package com.icc.qasker.quizhistory.dto.ferequest;

/** 폴더 생성 요청. 이름 검증(빈/공백/50자)은 서비스 계층에서 수행한다. */
public record CreateFolderRequest(String name) {}
