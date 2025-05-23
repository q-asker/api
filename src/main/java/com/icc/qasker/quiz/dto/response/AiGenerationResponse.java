package com.icc.qasker.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
@Getter
@AllArgsConstructor
public class AiGenerationResponse {
    private String title;
    private List<QuizGeneratedByAI> quiz;
}
