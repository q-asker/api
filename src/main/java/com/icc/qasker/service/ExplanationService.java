package com.icc.qasker.service;

import com.icc.qasker.dto.response.ResultResponse;
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
                    .orElseThrow(() -> new IllegalArgumentException("해당 문제 ID가 존재하지 않습니다: " + answer.getProblemId()));

            boolean isCorrect = problem.getCorrectAnswer().equalsIgnoreCase(answer.getUserAnswer());

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
