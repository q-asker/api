package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiQuestion(
    String content,
    List<GeminiSelection> selections,
    String quizExplanation,
    List<Integer> referencedPages) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GeminiSelection(String content, boolean correct, String explanation) {}
}
