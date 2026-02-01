package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;

public interface ProblemSetService {

    ProblemSetResponse getProblemSet(String problemSetId);
}

