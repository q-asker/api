package com.icc.qasker.quiz.service;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.dto.response.ProblemSetResponse;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import com.newrelic.api.agent.Trace;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ProblemSetService {

    private final ProblemSetRepository problemSetRepository;
    private final HashUtil hashUtil;

    @Trace(dispatcher = true)
    public ProblemSetResponse getProblemSet(String problemSetId) {

        ProblemSet problemSet = getProblemSetEntity(problemSetId);
        return toResponse(problemSet);
    }

    @Trace
    private ProblemSet getProblemSetEntity(String problemSetId) {

        long id = hashUtil.decode(problemSetId);

        return problemSetRepository.getProblemSetById(id)
            .orElseThrow(
                () -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND)
            );
    }

    @Trace
    private ProblemSetResponse toResponse(ProblemSet problemSet) {
        return ProblemSetResponse.of(problemSet);
    }

}
