package com.icc.qasker.quizset;

import com.icc.qasker.quizset.dto.ferequest.ChangeTitleRequest;
import com.icc.qasker.quizset.dto.feresponse.ChangeTitleResponse;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse;

public interface ProblemSetService {

  ProblemSetResponse getProblemSet(String problemSetId);

  ChangeTitleResponse changeProblemSetTitle(
      String userId, String problemSetId, ChangeTitleRequest request);
}
