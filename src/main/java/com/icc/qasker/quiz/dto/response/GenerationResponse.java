package com.icc.qasker.quiz.dto.response;

import com.icc.qasker.quiz.entity.ProblemId;
import com.icc.qasker.quiz.service.GenerationService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.bind.annotation.GetMapping;
@Getter
@AllArgsConstructor
public class GenerationResponse {
    private Long problemSetId;
    public static GenerationResponse of(Long problemSetId){
        return new GenerationResponse(problemSetId);
    }

}
