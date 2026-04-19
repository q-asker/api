package com.icc.qasker.quizset;

import com.icc.qasker.quizset.dto.feresponse.ExplanationResponse;

public interface ExplanationService {

  ExplanationResponse getExplanationByProblemSetId(String problemSetId);
}
