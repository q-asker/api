package com.icc.qasker.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class AiGenerationResponse {
    private String title;
    private List<QuizGeneratedByAI> quiz;
}
