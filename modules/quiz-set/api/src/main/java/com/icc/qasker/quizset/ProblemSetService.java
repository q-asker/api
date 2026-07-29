package com.icc.qasker.quizset;

import com.icc.qasker.quizset.dto.ferequest.ChangeTitleRequest;
import com.icc.qasker.quizset.dto.feresponse.ChangeTitleResponse;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quizset.dto.feresponse.RegenerationConditionResponse;

public interface ProblemSetService {

  ProblemSetResponse getProblemSet(String problemSetId);

  RegenerationConditionResponse getRegenerationCondition(String problemSetId);

  ChangeTitleResponse changeProblemSetTitle(
      String userId, String problemSetId, ChangeTitleRequest request);
}
