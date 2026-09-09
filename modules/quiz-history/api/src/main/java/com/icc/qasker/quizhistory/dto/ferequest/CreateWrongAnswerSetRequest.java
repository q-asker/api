package com.icc.qasker.quizhistory.dto.ferequest;

import jakarta.validation.constraints.NotBlank;

/** 오답 모아풀기 요청. 수집 범위는 폴더 하나이며, 요청자는 토큰에서 얻으므로 바디에 담지 않는다. */
public record CreateWrongAnswerSetRequest(
    @NotBlank(message = "folderId가 존재하지 않습니다.") String folderId) {}
