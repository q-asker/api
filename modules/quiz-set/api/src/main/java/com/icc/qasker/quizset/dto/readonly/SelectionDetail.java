package com.icc.qasker.quizset.dto.readonly;

/** Selection Entity의 read-only DTO. 모듈 경계를 넘어 Selection 데이터를 전달할 때 사용. */
public record SelectionDetail(String content, boolean correct) {}
