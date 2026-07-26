package com.icc.qasker.quizset.dto.airesponse;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProblemSetGeneratedEvent {

  private List<QuizGeneratedFromAI> quiz;

  @Getter
  @Setter
  public static class QuizGeneratedFromAI {

    private Integer number;
    private String title;
    private List<SelectionsOfAI> selections;
    private String explanation;
    private String bloomsLevel;
    private List<Integer> referencedPages;
    private String appliedInstruction;
    // 빈칸 순서대로의 허용변형(동의어). 정답 콤마 토큰과 짝지어 Problem.acceptedAnswers로 조립된다. null이면 허용변형 없음.
    private List<List<String>> acceptedAnswers;

    @Getter
    @Setter
    public static class SelectionsOfAI {

      private String content;
      private String explanation;
      private boolean correct;
    }
  }
}
