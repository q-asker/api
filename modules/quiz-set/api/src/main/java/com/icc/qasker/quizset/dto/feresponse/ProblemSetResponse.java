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
      String explanation,
      String appliedInstruction,
      // REAL_BLANK 정답 선지의 빈칸별 인정 답(quiz-level lift, contract.md §7.2). 그 외 null.
      List<List<String>> acceptedAnswers) {

    public record SelectionForFE(int id, String content, String explanation, boolean correct) {}
  }
}
