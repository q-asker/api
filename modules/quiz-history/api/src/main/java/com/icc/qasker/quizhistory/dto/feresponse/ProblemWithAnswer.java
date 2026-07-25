package com.icc.qasker.quizhistory.dto.feresponse;

import com.icc.qasker.quizset.dto.feresponse.Selection;
import java.util.List;

public record ProblemWithAnswer(
    int number,
    String title,
    int userAnswer,
    boolean correct,
    boolean inReview,
    List<Selection> selections,
    String textAnswer,
    // REAL_BLANK 정답 선지의 빈칸별 인정 답(quiz-level lift, contract.md §7.2). 그 외 null.
    List<List<String>> acceptedAnswers) {}
