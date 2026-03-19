package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.ferequest.ChangeTitleRequest;
import com.icc.qasker.quiz.dto.feresponse.ChangeTitleResponse;
import com.icc.qasker.quiz.dto.feresponse.ProblemSetResponse;

public interface ProblemSetService {

  ProblemSetResponse getProblemSet(String problemSetId);

  ChangeTitleResponse changeProblemSetTitle(
      String userId, String problemSetId, ChangeTitleRequest request);
}
