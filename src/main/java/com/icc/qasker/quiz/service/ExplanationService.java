package com.icc.qasker.quiz.service;

import com.icc.qasker.quiz.dto.response.ResultResponse;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.entity.ProblemId;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.dto.request.AnswerRequest;
import com.icc.qasker.quiz.dto.request.ExplanationRequest;
import com.icc.qasker.quiz.entity.Problem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.icc.qasker.quiz.util.ExplanationValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExplanationService {
    private final ProblemRepository problemRepository;

    public List<ResultResponse> gradeUserAnswers(ExplanationRequest request) {
        List<ResultResponse> responses = new ArrayList<>();

        Long problemSetId = request.getProblemSetId();

        for (AnswerRequest answer : request.getAnswers()) {
            ProblemId problemId = new ProblemId(problemSetId, answer.getNumber());

            Optional<Problem> optionalProblem = problemRepository.findById(problemId);

            Problem problem = optionalProblem.orElse(null);

            ExplanationValidator.checkOf(problem, answer);

            boolean isCorrect = problem.getCorrectAnswer().equals(answer.getUserAnswer());

            String explanationText = (problem.getExplanation() != null)
                    ? problem.getExplanation().getContent()
                    : "해설 없음";

            responses.add(new ResultResponse(
                    problemId.getNumber(),
                    problem.getCorrectAnswer(),
                    isCorrect,
                    explanationText
            ));
        }

        return responses;
    }
}