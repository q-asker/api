package com.slb.qasker.service;

import com.slb.qasker.dto.request.AnswerRequest;
import com.slb.qasker.dto.request.ExplanationRequest;
import com.slb.qasker.dto.response.ExplanationResponse;
import com.slb.qasker.dto.response.ResultResponse;
import com.slb.qasker.entity.Problem;
import com.slb.qasker.repository.ProblemRepository;
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
                    .orElse(new Problem(answer.getProblemId(), "?",
                            "문제 " + answer.getProblemId() + " 에 대한 해설이 존재하지 않습니다."));

            boolean isCorrect = problem.getCorrectAnswer().equalsIgnoreCase(answer.getUserAnswer());

            responses.add(new ResultResponse(
                    problem.getProblemId(),
                    problem.getCorrectAnswer(),
                    isCorrect,
                    problem.getExplanation()
            ));
        }

        return responses;
    }
}
