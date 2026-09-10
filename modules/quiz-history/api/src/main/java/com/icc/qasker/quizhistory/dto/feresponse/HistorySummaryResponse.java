package com.icc.qasker.quizhistory.dto.feresponse;

import com.icc.qasker.global.quiz.QuizType;
import com.icc.qasker.quizset.ProblemSetOrigin;
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
    Instant takenAt,
    String folderId,
    String folderName,
    ProblemSetOrigin origin) {}
