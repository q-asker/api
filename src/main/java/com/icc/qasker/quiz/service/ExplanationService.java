package com.icc.qasker.quiz.service;

import com.icc.qasker.quiz.dto.response.ResultResponse;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.Selection;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExplanationService {
    private final ProblemRepository problemRepository;

    public List<ResultResponse> getExplanationByProblemSetId(Long problemSetId) {
        List<Problem> problems = problemRepository.findByIdProblemSetId(problemSetId);
        List<ResultResponse> results = new ArrayList<>();

        for (Problem problem : problems) {
            String explanation = (problem.getExplanation() != null)
                    ? problem.getExplanation().getContent()
                    : "해설 없음";
            results.add(new ResultResponse(problem.getId().getNumber(), explanation));
        }

        return results;
    }
}