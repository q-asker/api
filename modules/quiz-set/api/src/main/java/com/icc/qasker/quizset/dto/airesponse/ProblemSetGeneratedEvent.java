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

    @Getter
    @Setter
    public static class SelectionsOfAI {

      private String content;
      private String explanation;
      private boolean correct;
    }
  }
}
