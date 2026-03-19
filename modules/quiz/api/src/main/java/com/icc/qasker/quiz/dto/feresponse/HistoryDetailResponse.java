package com.icc.qasker.quiz.dto.feresponse;

import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import java.time.Instant;
import java.util.List;

public record HistoryDetailResponse(
    String historyId,
    QuizType quizType,
    int totalCount,
    int score,
    Instant takenAt,
    List<ProblemWithAnswer> problems) {}
