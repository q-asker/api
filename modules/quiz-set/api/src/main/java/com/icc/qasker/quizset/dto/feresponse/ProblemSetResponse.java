package com.icc.qasker.quizset.dto.feresponse;

import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
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
      int number,
      String title,
      int userAnswer,
      boolean check,
      List<SelectionForFE> selections,
      String appliedInstruction) {

    public record SelectionForFE(int id, String content, boolean correct) {}
  }
}
