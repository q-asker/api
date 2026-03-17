package com.icc.qasker.quiz.dto.feresponse;

import com.icc.qasker.quiz.ExplanationStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExplanationResponse {

  private ExplanationStatus explanationStatus;
  private List<ResultResponse> results;
}
