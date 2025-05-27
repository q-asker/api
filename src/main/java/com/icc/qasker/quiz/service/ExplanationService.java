package com.icc.qasker.quiz.service;

import com.icc.qasker.quiz.dto.response.ResultResponse;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.entity.ProblemId;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.dto.request.AnswerRequest;
import com.icc.qasker.quiz.dto.request.ExplanationRequest;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.Selection;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

        for (AnswerRequest answerRequest : request.getAnswers()) {
            ProblemId problemId = new ProblemId(problemSetId, answerRequest.getNumber());

            Optional<Problem> optionalProblem = problemRepository.findById(problemId);

            Problem problem = optionalProblem.orElse(null);

            validate(problem, answerRequest);

            Long correctAnswerId = problem.getSelections().stream()
                    .filter(Selection::isCorrect)
                    .map(Selection::getId)
                    .findFirst()
                    .orElseThrow(() -> new CustomException(ExceptionMessage.INVALID_CORRECT_ANSWER));

            boolean isCorrect = correctAnswerId.equals(answerRequest.getUserAnswer());

            String explanationText = (problem.getExplanation() != null)
                    ? problem.getExplanation().getContent()
                    : "해설 없음";

            responses.add(new ResultResponse(
                    problemId.getNumber(),
                    correctAnswerId,
                    isCorrect,
                    explanationText
            ));
        }

        return responses;
    }
    private void validate(Problem problem,AnswerRequest answerRequest){
        if (problem == null){
            throw new CustomException(ExceptionMessage.PROBLEM_NOT_FOUND);
        }
        if (problem.getSelections().stream()
                .anyMatch(Selection::isCorrect)) {
            throw new CustomException(ExceptionMessage.INVALID_CORRECT_ANSWER);
        }
        if (answerRequest.getUserAnswer() == null) {
            throw new CustomException(ExceptionMessage.NULL_ANSWER_INPUT);
        }
    }
}