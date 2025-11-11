package com.icc.qasker.service;

import com.icc.qasker.ProblemSetService;
import com.icc.qasker.dto.response.ProblemSetResponse;
import com.icc.qasker.entity.ProblemSet;
import com.icc.qasker.error.CustomException;
import com.icc.qasker.error.ExceptionMessage;
import com.icc.qasker.mapper.ProblemSetResponseMapper;
import com.icc.qasker.repository.ProblemSetRepository;
import com.icc.qasker.util.HashUtil;
import com.newrelic.api.agent.Trace;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ProblemSetServiceImpl implements ProblemSetService {

    private final ProblemSetRepository problemSetRepository;
    private final HashUtil hashUtil;

    @Override
    @Trace(dispatcher = true)
    public ProblemSetResponse getProblemSet(String problemSetId) {

        long id = decode(problemSetId);
        ProblemSet problemSet = getProblemSetEntity(id);
        return toResponse(problemSet);
    }

    @Trace
    private ProblemSet getProblemSetEntity(long id) {
        return problemSetRepository.findById(id)
            .orElseThrow(
                () -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND)
            );
    }

    @Trace
    public long decode(String hashId) {
        return hashUtil.decode(hashId);
    }

    @Trace
    private ProblemSetResponse toResponse(ProblemSet problemSet) {
        return ProblemSetResponseMapper.fromEntity(problemSet);
    }
}

