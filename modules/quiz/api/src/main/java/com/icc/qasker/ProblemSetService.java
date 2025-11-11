package com.icc.qasker;

import com.icc.qasker.dto.response.ProblemSetResponse;

public interface ProblemSetService {
    
    ProblemSetResponse getProblemSet(String problemSetId);
}

