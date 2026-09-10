package com.icc.qasker.quizset.dto.feresponse;

import com.icc.qasker.global.quiz.QuizType;
import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.ProblemSetOrigin;
import java.util.List;

public record ProblemSetResponse(
    String sessionId,
    String problemSetId,
    String title,
    GenerationStatus generationStatus,
    QuizType quizType,
    Integer totalCount,
    ProblemSetOrigin origin,
    List<QuizForFe> quiz) {

  public record QuizForFe(
      int number,
      String title,
      int userAnswer,
      boolean check,
      List<SelectionForFE> selections,
      String explanation,
      String appliedInstruction) {

    public record SelectionForFE(int id, String content, String explanation, boolean correct) {}
  }
}
