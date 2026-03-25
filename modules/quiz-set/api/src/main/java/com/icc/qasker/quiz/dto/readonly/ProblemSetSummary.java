package com.icc.qasker.quiz.dto.readonly;

import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import java.time.Instant;

/** ProblemSet Entity의 read-only DTO. 모듈 경계를 넘어 ProblemSet 데이터를 전달할 때 사용. */
public record ProblemSetSummary(
    Long id, QuizType quizType, int totalQuizCount, String title, Instant createdAt) {}
