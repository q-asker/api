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
    String textAnswer) {}
