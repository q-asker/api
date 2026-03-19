package com.icc.qasker.quiz.dto.feresponse;

import com.icc.qasker.quiz.GenerationStatus;
import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import java.util.List;

public record ProblemSetResponse(
    String sessionId,
    String problemSetId,
    String title,
    GenerationStatus generationStatus,
    QuizType quizType,
    Integer totalCount,
    List<QuizForFe> quiz) {

  public record QuizForFe(
      int number, String title, int userAnswer, boolean check, List<SelectionForFE> selections) {

    public record SelectionForFE(int id, String content, boolean correct) {}
  }
}
