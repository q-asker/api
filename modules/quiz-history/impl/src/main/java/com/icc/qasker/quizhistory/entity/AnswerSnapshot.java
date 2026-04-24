package com.icc.qasker.quizhistory.entity;

public record AnswerSnapshot(int number, int userAnswer, boolean inReview, String textAnswer) {}
