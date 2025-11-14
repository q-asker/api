package com.icc.qasker.quiz.service;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.ExplanationService;
import com.icc.qasker.quiz.dto.response.ExplanationResponse;
import com.icc.qasker.quiz.dto.response.ResultResponse;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ReferencedPage;
import com.icc.qasker.quiz.repository.ProblemRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExplanationServiceImpl implements ExplanationService {

    private final HashUtil hashUtil;
    private final ProblemRepository problemRepository;

    @Override
    @Transactional(readOnly = true)
    public ExplanationResponse getExplanationByProblemSetId(String problemSetId) {
        long id = hashUtil.decode(problemSetId);
        List<Problem> problems = problemRepository.findByIdProblemSetId(id);
        if (problems.isEmpty()) {
            throw new CustomException(ExceptionMessage.PROBLEM_NOT_FOUND);
        }

        List<ResultResponse> results = new ArrayList<>();
        for (Problem problem : problems) {
            String explanation = (problem.getExplanation() != null)
                ? problem.getExplanation().getContent()
                : "해설 없음";
            List<Integer> pages = problem.getReferencedPages()
                .stream()
                .map(ReferencedPage::getPageNumber)
                .toList();

            results.add(new ResultResponse(problem.getId().getNumber(), explanation, pages));
        }

        return new ExplanationResponse(results);
    }
}

