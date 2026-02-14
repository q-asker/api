package com.icc.qasker.quiz.service;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.ProblemSetService;
import com.icc.qasker.quiz.dto.response.ProblemSetResponse;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.mapper.ProblemSetResponseMapper;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class ProblemSetServiceImpl implements ProblemSetService {

    private final ProblemSetResponseMapper problemSetResponseMapper;
    private final ProblemSetRepository problemSetRepository;
    private final HashUtil hashUtil;

    @Override
    @Transactional(readOnly = true)
    public ProblemSetResponse getProblemSet(String problemSetId) {

        long id = hashUtil.decode(problemSetId);
        ProblemSet problemSet = getProblemSetEntity(id);
        return problemSetResponseMapper.fromEntity(problemSet);
    }

    private ProblemSet getProblemSetEntity(long id) {
        return problemSetRepository.findById(id)
            .orElseThrow(
                () -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND)
            );
    }
}

