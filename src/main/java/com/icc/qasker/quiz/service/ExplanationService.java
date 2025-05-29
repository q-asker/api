package com.icc.qasker.quiz.service;

import com.icc.qasker.quiz.dto.response.ExplanationResponse;
import com.icc.qasker.quiz.dto.response.ResultResponse;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.repository.ProblemRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExplanationService {

    private final ProblemRepository problemRepository;

    public ExplanationResponse getExplanationByProblemSetId(Long problemSetId) {
        List<Problem> problems = problemRepository.findByIdProblemSetId(problemSetId);
        List<ResultResponse> results = new ArrayList<>();

        for (Problem problem : problems) {
            String explanation = (problem.getExplanation() != null)
                ? problem.getExplanation().getContent()
                : "해설 없음";
            results.add(new ResultResponse(problem.getId().getNumber(), explanation));
        }

        return new ExplanationResponse(results);
    }
}