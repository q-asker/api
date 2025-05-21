package com.icc.qasker.service;

import com.icc.qasker.dto.response.ResultResponse;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.repository.ProblemRepository;
import com.icc.qasker.dto.request.AnswerRequest;
import com.icc.qasker.dto.request.ExplanationRequest;
import com.icc.qasker.entity.Problem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExplanationService {
    private final ProblemRepository problemRepository;

    public List<ResultResponse> gradeUserAnswers(ExplanationRequest request){
        List<ResultResponse> responses = new ArrayList<>();

        for(AnswerRequest answer : request.getAnswers()){
            Problem problem = problemRepository.findById(answer.getProblemId())
                    .orElseThrow(() -> new CustomException(ExceptionMessage.DATA_NOT_FOUND));

            if (problem.getCorrectAnswer() == null) {
                throw new CustomException(ExceptionMessage.INVALID_CORRECT_ANSWER);
            }

            if (answer.getUserAnswer() == null) {
                throw new CustomException(ExceptionMessage.NULL_ANSWER_INPUT);
            }


            boolean isCorrect = Long.valueOf(problem.getCorrectAnswer()).equals(answer.getUserAnswer());

            String explanationText = problem.getExplanation() != null
                    ? problem.getExplanation().getContent()
                    : "해당 문제에 대한 해설이 존재하지 않습니다.";

            responses.add(new ResultResponse(
                    problem.getId(),
                    problem.getCorrectAnswer(),
                    isCorrect,
                    explanationText
            ));
        }

        return responses;
    }
}
