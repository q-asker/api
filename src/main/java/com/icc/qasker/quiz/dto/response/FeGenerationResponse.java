package com.icc.qasker.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class FeGenerationResponse {
    private Long problemSetId;
    private String title;
    private List<QuizForFe> quiz;
}
