package com.icc.qasker.quizhistory.dto.feresponse;

import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import java.time.Instant;

public record HistorySummaryResponse(
    String problemSetId,
    String title,
    Instant createdAt,
    String historyId,
    QuizType quizType,
    int totalCount,
    boolean completed,
    Integer score,
    Instant takenAt) {}
