package com.icc.qasker.quizhistory.dto.feresponse;

import com.icc.qasker.quiz.dto.feresponse.Selection;
import java.util.List;

public record ProblemWithAnswer(
    int number,
    String title,
    int userAnswer,
    boolean correct,
    boolean inReview,
    List<Selection> selections) {}
