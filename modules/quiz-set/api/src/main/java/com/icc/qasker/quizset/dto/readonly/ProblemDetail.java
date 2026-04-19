package com.icc.qasker.quizset.dto.readonly;

import java.util.List;

/** Problem Entity의 read-only DTO. 모듈 경계를 넘어 Problem 데이터를 전달할 때 사용. */
public record ProblemDetail(int number, String title, List<SelectionDetail> selections) {}
