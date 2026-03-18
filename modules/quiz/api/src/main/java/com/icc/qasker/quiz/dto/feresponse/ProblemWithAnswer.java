package com.icc.qasker.quiz.dto.feresponse;

import java.util.List;

public record ProblemWithAnswer(
    int number, String title, int userAnswer, boolean correct, List<Selection> selections) {}
