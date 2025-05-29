package com.icc.qasker.quiz.dto.response;
import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.entity.ProblemId;
import com.icc.qasker.quiz.service.GenerationService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.bind.annotation.GetMapping;
@Getter
@AllArgsConstructor
public class GenerationResponse {
    private String problemSetId;
    public static GenerationResponse of(String problemSetId) {
        return new GenerationResponse(problemSetId);
    }

}
